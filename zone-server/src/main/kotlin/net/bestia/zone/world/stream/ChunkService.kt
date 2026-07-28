package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.derived.AgentProfile
import net.bestia.worldgen.derived.DerivedStore
import net.bestia.worldgen.store.ChunkCache
import net.bestia.worldgen.store.ChunkStore
import net.bestia.worldgen.store.MemoryBlobStore
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater

/**
 * The zone-server's view of the voxel world: the authoritative merged state, its derived structures, and the
 * encoded payloads the network layer sends.
 *
 * This is the piece `worldgen/` was always missing a caller for. `ChunkStore.merged` is the only way to read
 * a chunk - there is no API handing out a base without its delta - so the view the client is shown and the
 * view line of sight and movement validation will be answered from are the same object by construction
 * rather than by discipline.
 *
 * ### Single threaded, and it has to be
 *
 * `ChunkStore`, `ChunkDelta` and `DerivedStore` are all documented as not thread safe: a chunk has one
 * owning node, and here that means one owning *thread*. Everything in this class runs on `zone-tick`. The
 * only work that leaves it is the Netty write, which takes an already-built buffer.
 *
 * ### Two caches, different jobs
 *
 * [ChunkCache]'s hot tier holds *decoded* chunks, half a megabyte each, for the server's own voxel queries.
 * [encoded] holds *encoded and compressed* payloads, three kilobytes each, for the wire. Keeping them apart
 * is what makes thirty players entering the same area cost one materialise, one encode and one deflate
 * between them instead of thirty of each - and it means a chunk the server is reasoning about does not have
 * to be one it is also sending.
 */
@Service
class ChunkService(
  private val worldService: WorldService,
  private val settings: ChunkStreamConfig
) {

  /**
   * One edit batch applied to one chunk, waiting to be told to the clients that hold it.
   *
   * @property edits voxel index to `(blockId shl 8) or occupancy`, coalesced - an index appears once
   * @property baked the chunk outgrew its delta and was baked; its whole content was rewritten, so a patch
   *   describes it correctly but a subscriber that has fallen behind cannot be caught up from patches alone
   */
  data class ChunkChange(
    val chunk: ChunkPos,
    val edits: Map<Int, Int>,
    val fromRevision: Int,
    val toRevision: Int,
    val baked: Boolean
  )

  /** An encoded, possibly compressed chunk payload, ready to put on a socket. */
  class Encoded(
    val chunk: ChunkPos,
    val revision: Int,
    val compression: ChunkDataSMSG.Compression,
    val payload: ByteArray,
    val baseHash: Long,
    /** Size before compression, for logging and for the patch-versus-snapshot decision. */
    val encodedBytes: Int
  )

  private class Lru<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(16, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, V>?) = size > capacity
  }

  private val loaded by lazy { build() }

  private class Loaded(
    val config: WorldConfig,
    val wrap: WorldWrap,
    val columns: ChunkColumnSource,
    val store: ChunkStore,
    val derived: DerivedStore
  )

  /**
   * Whether the world is generated yet.
   *
   * `WorldService.load()` runs as the first boot runner, so this is only ever false for the window between
   * bean creation and that runner - which the tick loop can overlap with, since it is a boot runner too.
   * Asking is cheaper than ordering the two, and getting it wrong would generate a whole world on the tick
   * thread inside one tick.
   */
  val isReady get() = worldService.isLoaded

  /** Revision of each chunk that has ever been edited. Absent means zero - untouched. */
  private val revisions = HashMap<ChunkPos, Int>()

  private val encoded = Lru<EncodedKey, Encoded>(settings.encodedCacheCapacity)

  /** Base hashes are keyed on position alone: the base of a coordinate does not change with its revision. */
  private val baseHashes = Lru<ChunkPos, Long>(settings.encodedCacheCapacity)

  /**
   * Vertical slab range per horizontal chunk column. See [surfaceSlabsOf] for why this cache is load bearing.
   *
   * Keyed on `(x, y)` only, and never invalidated, because the heightfield it derives from is immutable. Held
   * far more generously than the chunk caches: an entry is two ints against half a megabyte for a decoded
   * chunk, and a hit here is what keeps recomputing a manifest off the tick thread's critical path.
   */
  private val slabs = Lru<Pair<Int, Int>, IntRange>(settings.slabCacheCapacity)

  private data class EncodedKey(val chunk: ChunkPos, val revision: Int)

  private val pending = LinkedHashMap<ChunkPos, MutableMap<Int, Int>>()
  private val pendingFrom = HashMap<ChunkPos, Int>()
  private val pendingBaked = HashSet<ChunkPos>()

  val config: WorldConfig get() = loaded.config

  val wrap: WorldWrap get() = loaded.wrap

  private fun build(): Loaded {
    val generated = worldService.generated
    val config = generated.config

    val cache = ChunkCache(
      seed = config.seed,
      pipelineVersion = generated.world.pipelineVersion,
      generate = { generated.materializer.materialize(it) },
      hotCapacity = settings.hotChunkCapacity
    )

    val store = ChunkStore(config, cache, MemoryBlobStore(), onChanged = ::onChunkChanged)

    // Reads merged voxels, never a base: a walkable span or an opacity cell computed from terrain that
    // ignores what players built would be wrong in exactly the places it matters most.
    val derived = DerivedStore(voxels = store::merged, agent = AgentProfile())

    LOG.info {
      "Chunk streaming ready: ${config.chunkSize}x${config.chunkSize}x${config.chunkHeight} chunks, " +
          "view radius ${settings.viewRadiusChunks} (${settings.chunksAcrossView}x${settings.chunksAcrossView})"
    }

    return Loaded(config, WorldWrap(config), generated.columns, store, derived)
  }

  /** Normalises a client-supplied or computed address across the world seam. Never wraps z; up is not a loop. */
  fun normalise(chunk: ChunkPos): ChunkPos = loaded.wrap.normalise(chunk)

  /**
   * Which vertical slabs the terrain surface passes through in this chunk column.
   *
   * Usually one value, occasionally two where a column straddles a 256 m boundary. This is what lets the
   * subscription be vertically thin without ever being vertically wrong: it reads the *heightfield* rather
   * than trusting the player's own `z`, so a player standing at sea level under a 300 m mountain is still
   * sent the mountain.
   *
   * ### Cached, and it must be
   *
   * The uncached call is not cheap: `heights` does a feature-index query and then evaluates multi-octave
   * noise for all 1024 columns of the chunk. A manifest asks about a whole view volume - 121 chunks at the
   * default radius - and a manifest is recomputed every time a player crosses a chunk boundary, which at
   * walking pace is every thirty-two metres. Uncached that is over a hundred thousand height evaluations on
   * the tick thread against a fifty-millisecond budget.
   *
   * Caching is sound rather than a gamble: the answer is a pure function of the base heightfield and the
   * vector features, both immutable after world creation. Player edits change *voxels*, never the
   * heightfield, so no edit can invalidate this - which is why there is no invalidation path and should not
   * be one.
   */
  fun surfaceSlabsOf(column: ChunkPos): IntRange = slabs.getOrPut(column.x to column.y) {
    computed++
    computeSurfaceSlabs(column)
  }

  /**
   * The cached slab range, or `null` if it has not been computed yet.
   *
   * Lets a caller distinguish "free" from "expensive" and spend a budget accordingly, rather than discovering
   * the cost after paying it. See [ChunkStreamConfig.slabComputationsPerTick].
   */
  fun cachedSlabsOf(column: ChunkPos): IntRange? = slabs[column.x to column.y]

  private var computed = 0

  /**
   * How many slab ranges have actually been computed rather than served from cache.
   *
   * Exposed so the cache can be tested for what it is - a performance property - without a timing assertion.
   * A test that recomputes the same manifest and watches this stay still is deterministic; one that watches a
   * stopwatch is not.
   */
  val slabComputations get() = computed

  private fun computeSurfaceSlabs(column: ChunkPos): IntRange {
    val heights = loaded.columns.heights(ChunkPos(column.x, column.y, 0), 0)

    var lowest = Double.POSITIVE_INFINITY
    var highest = Double.NEGATIVE_INFINITY

    for (localY in 0 until loaded.config.chunkSize) {
      for (localX in 0 until loaded.config.chunkSize) {
        val height = heights[localX, localY]
        if (height < lowest) lowest = height
        if (height > highest) highest = height
      }
    }

    // A column source that produced nothing finite is a bug rather than a flat world, but refusing to guess
    // is better than subscribing to a slab chosen by an infinity.
    if (!lowest.isFinite() || !highest.isFinite()) {
      LOG.warn { "Column heights for $column are not finite; falling back to the sea-level slab" }
      return 0..0
    }

    // Sea level is included, so a coastal column offers both the seabed slab and the one holding the water
    // surface even when the terrain itself never reaches it.
    val floor = minOf(lowest, loaded.config.seaLevel)
    val ceiling = maxOf(highest, loaded.config.seaLevel)

    return loaded.config.chunkZOf(floor)..loaded.config.chunkZOf(ceiling)
  }

  /** The authoritative merged chunk: base with every edit applied, or the baked blob. */
  fun merged(chunk: ChunkPos): VoxelChunk = loaded.store.merged(chunk)

  fun revisionOf(chunk: ChunkPos): Int = revisions[chunk] ?: 0

  fun derived(): DerivedStore = loaded.derived

  /**
   * The payload for a chunk at its current revision, encoding and compressing only on a miss.
   *
   * Compression is chosen per payload rather than applied unconditionally, mirroring `DeflatedBlobStore`:
   * a chunk that is entirely air or entirely bedrock encodes to thirteen bytes and *grows* to nineteen if
   * deflated, and most of a world is one of those two.
   */
  fun encodedOf(chunk: ChunkPos): Encoded {
    val revision = revisionOf(chunk)
    encoded[EncodedKey(chunk, revision)]?.let { return it }

    val voxels = loaded.store.merged(chunk)
    val raw = RleCodec.encode(voxels)

    val deflated = if (raw.size >= settings.deflateMinimumBytes) deflate(raw) else null
    val useDeflate = deflated != null && deflated.size < raw.size

    val result = Encoded(
      chunk = chunk,
      revision = revision,
      compression = if (useDeflate) ChunkDataSMSG.Compression.DEFLATE else ChunkDataSMSG.Compression.NONE,
      payload = if (useDeflate) deflated!! else raw,
      baseHash = baseHashOf(chunk),
      encodedBytes = raw.size
    )

    encoded[EncodedKey(chunk, revision)] = result
    return result
  }

  fun dataMessageFor(chunk: ChunkPos): ChunkDataSMSG {
    val payload = encodedOf(chunk)

    return ChunkDataSMSG(
      chunk = payload.chunk,
      revision = payload.revision,
      encoding = ChunkDataSMSG.Encoding.RLE_V2,
      compression = payload.compression,
      payload = payload.payload,
      baseHash = payload.baseHash
    )
  }

  private fun baseHashOf(chunk: ChunkPos): Long = baseHashes.getOrPut(chunk) { loaded.store.baseHash(chunk) }

  /**
   * Sets one voxel, bumping the chunk's revision and queueing the change for broadcast.
   *
   * The single entry point for terrain mutation. It is deliberately the only place that touches
   * `ChunkStore.edit`, so no caller can change the world without the revision moving and the subscribers
   * being told - the two things that would otherwise silently desynchronise every client holding the chunk.
   */
  fun setBlock(chunk: ChunkPos, localX: Int, localY: Int, localZ: Int, block: BlockType) {
    val from = revisionOf(chunk)
    val baked = loaded.store.edit(chunk, localX, localY, localZ, block)
    val to = revisionOf(chunk)

    val occupancy = if (block == BlockType.AIR) Occupancy.EMPTY else Occupancy.FULL
    val index = ChunkCoords.voxelIndex(loaded.config, localX, localY, localZ)

    pending.getOrPut(chunk) { LinkedHashMap() }[index] = ChunkPatchCodec.pack(block.id, occupancy)
    pendingFrom.putIfAbsent(chunk, from)
    if (baked) pendingBaked.add(chunk)

    LOG.debug { "Edited $chunk local ($localX,$localY,$localZ) to $block, revision $from -> $to" }
  }

  /**
   * Called by [ChunkStore] whenever a chunk's contents change.
   *
   * Bumps the revision and marks the derived structures stale. The rebuild itself is *not* done here - it
   * is queued and paid for out of a per-tick budget, so a player placing a fence cannot cost the zone
   * thread a walkability rebuild in the middle of a tick.
   */
  private fun onChunkChanged(chunk: ChunkPos) {
    revisions[chunk] = revisionOf(chunk) + 1
    loaded.derived.invalidate(chunk)
  }

  /**
   * Hands over every change since the last call and forgets them.
   *
   * Drained once per tick by [ChunkStreamSystem]. Coalescing happens on the way in - the pending map is
   * keyed by voxel index - so a player holding a dig key down produces one edit per voxel per tick rather
   * than one message per keypress.
   */
  fun drainChanges(): List<ChunkChange> {
    if (pending.isEmpty()) return emptyList()

    val changes = pending.map { (chunk, edits) ->
      ChunkChange(
        chunk = chunk,
        edits = edits.toMap(),
        fromRevision = pendingFrom[chunk] ?: 0,
        toRevision = revisionOf(chunk),
        baked = chunk in pendingBaked
      )
    }

    pending.clear()
    pendingFrom.clear()
    pendingBaked.clear()

    return changes
  }

  /** Spends the per-tick derived-structure rebuild budget. Returns how many were rebuilt. */
  fun rebuildDerived(): Int =
    if (settings.derivedRebuildsPerTick == 0) 0 else loaded.derived.rebuild(settings.derivedRebuildsPerTick)

  private fun deflate(blob: ByteArray): ByteArray {
    val deflater = Deflater(settings.deflateLevel)
    try {
      deflater.setInput(blob)
      deflater.finish()

      val out = ByteArrayOutputStream(blob.size / 2 + 32)
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

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
