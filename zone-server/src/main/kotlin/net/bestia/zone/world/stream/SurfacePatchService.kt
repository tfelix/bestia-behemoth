package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.worldgen.lod.PatchGrid
import net.bestia.worldgen.lod.PatchPos
import net.bestia.worldgen.lod.SurfacePatchCodec
import net.bestia.worldgen.lod.SurfacePatchSampler
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.zip.Deflater

/**
 * Produces and caches the coarse surface patches that let terrain be drawn past the full-detail ring.
 *
 * ### Nothing here runs on the tick thread
 *
 * Sampling a patch is one feature query plus 4 225 heights, which is a few chunk columns' worth of work -
 * and `ChunkStreamConfig.slabComputationsPerTick` exists because doing even a view volume's worth of that
 * inside one tick stalls the zone thread measurably. So it is not budgeted, it is *moved*: a small pool
 * samples, the tick thread only ever asks whether a payload is ready yet.
 *
 * That is safe, and not by luck. `SurfacePatchSampler` holds no mutable state, the world's layers are plain
 * arrays, and the feature index is frozen once generation finishes. `MapTileService` already renders map
 * tiles off-thread on the same guarantee and documents `ChunkService` as the one thing it must never reach;
 * the same rule applies here.
 *
 * ### One cache, and it never needs invalidating
 *
 * A patch comes from the heightfield, which no player edit can move, so a payload is correct for as long as
 * the world lives. The cache is therefore of finished *wire bytes* rather than of decoded patches - the
 * server has no reason to read a patch itself, only to send it - and a hit costs a map lookup.
 *
 * Bounded all the same. Never *invalidating* an entry is not a reason to never evict one: a 128 km world
 * holds a quarter of a million level-0 patches, and a cache that only grows is a slow leak that looks like
 * correct behaviour right up until it does not. Evicting merely costs a resample.
 */
@Service
class SurfacePatchService(
  private val worldService: WorldService,
  private val settings: ChunkStreamConfig
) {

  /** A finished payload: encoded, possibly deflated, ready to put on a socket. */
  class Encoded(val pos: PatchPos, val compressed: Boolean, val payload: ByteArray)

  private val workers = Executors.newFixedThreadPool(RENDER_THREADS) { runnable ->
    Thread(runnable, "patch-sample").apply { isDaemon = true }
  }

  /**
   * Finished payloads, bounded and least-recently-read-first.
   *
   * Synchronised rather than concurrent because the two sides are not symmetric: the sampling pool writes a
   * few times a second and the tick thread reads a handful per player per tick, so the lock is never
   * contended, and an access-ordered [Lru] cannot be had lock-free anyway.
   */
  private val ready = Collections.synchronizedMap(Lru<PatchPos, Encoded>(settings.patchCacheCapacity))

  /** Positions a worker is on. Also the de-duplicator: two players asking at once must sample once. */
  private val inFlight = ConcurrentHashMap.newKeySet<PatchPos>()

  private val sampler by lazy { SurfacePatchSampler.of(worldService.generated) }

  val isReady get() = worldService.isLoaded

  /**
   * Identity of the patches this world produces, for a client deciding whether its stored copies still apply.
   *
   * Folds the world row's generation identity with the payload format, because a client cache can be wrong in
   * two independent ways - the ground moved, or the bytes describing it changed shape - and neither is
   * recoverable once a stale patch has been trusted. Narrowed to 32 bits for the wire; a collision between
   * two worlds a player happens to have both cached is a cosmetic risk, not a correctness one, and the field
   * costs half as much.
   */
  val version: Int
    get() {
      val record = worldService.record
      var folded = record.pipelineVersion * 31 + record.shapeVersion
      folded = folded * 31 + SurfacePatchCodec.VERSION
      val narrowed = (folded xor (folded ushr 32)).toInt()

      // Zero is the client's "the server sent none, do not persist" signal, so a world that happened to fold
      // to it would silently lose its disk cache forever. One collision in four billion, and one line to rule
      // out a failure mode that would never be diagnosed.
      return if (narrowed == 0) 1 else narrowed
    }

  /** The payload if it has been sampled, or null - in which case [request] has been told to start on it. */
  fun cached(pos: PatchPos): Encoded? = ready[pos]

  /**
   * Asks for [pos] to be sampled, if nobody is already on it.
   *
   * Returns immediately. The tick thread calls this while building a manifest and announces the patch on a
   * later tick once [cached] answers - which is the same "a manifest grows instead" shape the slab budget
   * already uses, and needs no budget here because the work is not on this thread to begin with.
   */
  fun request(pos: PatchPos) {
    if (cached(pos) != null || !inFlight.add(pos)) return

    workers.execute {
      try {
        ready[pos] = encode(pos)
      } catch (e: Exception) {
        // Left out of `ready`, so the next manifest asks again. A patch that cannot be sampled is a bug in
        // generation, not a transient failure - but dropping the connection over distant scenery would be
        // a much worse answer than terrain that stops early.
        LOG.warn(e) { "Could not sample surface patch $pos" }
      } finally {
        inFlight.remove(pos)
      }
    }
  }

  /** How many patches are cached, so a test can assert the cache is a cache without timing anything. */
  val cachedPatches get() = ready.size

  private fun encode(pos: PatchPos): Encoded {
    val raw = SurfacePatchCodec.encode(sampler.sample(pos))
    val deflated = deflate(raw)

    // Unlike a chunk there is no "too small to bother" case: a patch is a fixed 25 kB and deflates to a
    // tenth of that or less on any ground, so the test the chunk path makes per payload has one answer here.
    return Encoded(pos, compressed = deflated.size < raw.size, payload = if (deflated.size < raw.size) deflated else raw)
  }

  private fun deflate(blob: ByteArray): ByteArray {
    val deflater = Deflater(settings.deflateLevel)
    try {
      deflater.setInput(blob)
      deflater.finish()

      val out = ByteArrayOutputStream(blob.size / 8 + 32)
      val buffer = ByteArray(8192)
      while (!deflater.finished()) {
        val n = deflater.deflate(buffer)
        if (n == 0) break
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    } finally {
      deflater.end()
    }
  }

  @PreDestroy
  fun shutdown() {
    workers.shutdownNow()
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * Sampling threads. Two, and bounded rather than elastic for `MapTileService`'s reason: a burst of
     * requests must not become a burst of concurrent samples, each holding its own scratch.
     */
    private const val RENDER_THREADS = 2
  }
}
