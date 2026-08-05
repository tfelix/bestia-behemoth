package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkColumnSource
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.ColumnHeights
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.core.WorldWrap
import net.bestia.worldgen.derived.AgentProfile
import net.bestia.worldgen.derived.ChunkDelta
import net.bestia.worldgen.derived.DerivedStore
import net.bestia.worldgen.store.ChunkCache
import net.bestia.worldgen.store.ChunkStore
import net.bestia.worldgen.store.MemoryBlobStore
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.worldgen.voxel.CarveRules
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
   * One batch of removals applied to one chunk, waiting to be told to the clients that hold it.
   *
   * @property removals packed `(voxelIndex shl 8) or remainingOccupancy`, sorted, coalesced - an index appears
   *   once, at the lowest occupancy it reached during the tick
   * @property baked the chunk outgrew its delta and was baked; its whole content was rewritten, so a patch
   *   describes it correctly but a subscriber that has fallen behind cannot be caught up from patches alone
   */
  data class ChunkChange(
    val chunk: ChunkPos,
    val removals: IntArray,
    val fromRevision: Int,
    val toRevision: Int,
    val baked: Boolean
  ) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other !is ChunkChange) return false

      return chunk == other.chunk &&
          fromRevision == other.fromRevision &&
          toRevision == other.toRevision &&
          baked == other.baked &&
          removals.contentEquals(other.removals)
    }

    override fun hashCode(): Int {
      var result = chunk.hashCode()
      result = 31 * result + removals.contentHashCode()
      result = 31 * result + fromRevision
      result = 31 * result + toRevision
      result = 31 * result + baked.hashCode()
      return result
    }
  }

  /**
   * One voxel a carve took, and what was there before it did.
   *
   * The prior block is reported because it cannot be recovered afterwards: a removal records how much is left,
   * not what it was, so once the delta is applied the only way back to "this was rich iron ore" is a second
   * read of the base. `OreBlocks.yieldOf` is the caller that needs it - it has existed since the ore palette
   * landed and has never had one.
   *
   * @property priorOccupancy and [remainingOccupancy] together give how much came out, which a yield that
   *   scales with volume will want. A voxel is exhausted when [remainingOccupancy] is zero, and that is the
   *   moment to hand over an item rather than fractions of one.
   *
   * Global voxel coordinates rather than chunk-local ones, because the caller that matters wants to put
   * something *in the world* - a collectable where the ore was - and would otherwise have to undo the
   * localisation this class just did.
   */
  data class CarvedVoxel(
    val chunk: ChunkPos,
    val voxelX: Long,
    val voxelY: Long,
    val voxelZ: Int,
    val priorBlock: BlockType,
    val priorOccupancy: Int,
    val remainingOccupancy: Int
  ) {
    /** Fraction of a whole voxel that came out, in `[0,1]`. */
    val volumeRemoved get() = (priorOccupancy - remainingOccupancy) / Occupancy.FULL.toDouble()

    /** True when nothing of this voxel is left, so whatever it was made of is now entirely in hand. */
    val exhausted get() = remainingOccupancy == Occupancy.EMPTY
  }

  /** What one brush actually did. Empty when the brush found nothing it was allowed to take. */
  data class CarveResult(
    val voxels: List<CarvedVoxel>,
    /** Chunks whose content changed, so a caller can tell how far a brush near a border reached. */
    val chunks: Set<ChunkPos>
  ) {
    val isEmpty get() = voxels.isEmpty()

    companion object {
      val NOTHING = CarveResult(emptyList(), emptySet())
    }
  }

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
   * Vertical slabs per horizontal chunk column. See [surfaceSlabsOf] for why this cache is load bearing.
   *
   * Keyed on `(x, y)` only, and never invalidated, because the heightfield it derives from is immutable. Held
   * far more generously than the chunk caches: an entry is a two- or three-element array against half a
   * megabyte for a decoded chunk, and a hit here is what keeps recomputing a manifest off the tick thread's
   * critical path.
   */
  private val slabs = Lru<Pair<Int, Int>, IntArray>(settings.slabCacheCapacity)

  private data class EncodedKey(val chunk: ChunkPos, val revision: Int)

  /** Per chunk, voxel index to the lowest occupancy it reached this tick. */
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
   * Which vertical slabs hold a *surface* in this chunk column - somewhere the world stops being one thing and
   * starts being another, and so somewhere there is geometry to draw.
   *
   * Two surfaces qualify: the terrain, from the column's lowest ground to its highest, and the sea, whose slabs
   * [seaSurfaceSlabs] works out. Usually that is one or two slabs and never normally more than four, so this
   * is what lets the subscription be vertically thin without being vertically wrong: it reads the
   * *heightfield* rather than trusting the player's own `z`, so a player standing at sea level under a 300 m
   * mountain is still offered the mountain.
   *
   * ### Not contiguous, which is the whole point
   *
   * An earlier version returned the span from the seabed up to the water surface, which for the 2.5 km ocean
   * margin is a seabed 800 m down and so five slabs - four of them solid water fill that the client meshes to
   * nothing, after the server has generated them. The gap between the two surfaces is exactly what a caller
   * wants skipped, so the return type has to be able to express a hole.
   *
   * ### Sea level stands in for the water surface
   *
   * Real water level varies per column - lakes and rivers sit above it - but sampling that here would add a
   * second field query to a method whose entire justification is being cheap. A mountain lake's surface is
   * within metres of its own terrain, which the terrain term already covers; the ocean is the case where the
   * two diverge by hundreds of metres, and there sea level is exact. Getting *which slab* that is wrong is what
   * made open water render as nothing at all - see [seaSurfaceSlabs].
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
  fun surfaceSlabsOf(column: ChunkPos): IntArray = slabs.getOrPut(column.x to column.y) {
    computed++
    computeSurfaceSlabs(column)
  }

  /**
   * The cached slabs, or `null` if they have not been computed yet.
   *
   * Lets a caller distinguish "free" from "expensive" and spend a budget accordingly, rather than discovering
   * the cost after paying it. See [ChunkStreamConfig.slabComputationsPerTick].
   */
  fun cachedSlabsOf(column: ChunkPos): IntArray? = slabs[column.x to column.y]

  private var computed = 0

  /**
   * How many columns' slabs have actually been computed rather than served from cache.
   *
   * Exposed so the cache can be tested for what it is - a performance property - without a timing assertion.
   * A test that recomputes the same manifest and watches this stay still is deterministic; one that watches a
   * stopwatch is not.
   */
  val slabComputations get() = computed

  /**
   * Column heights per horizontal chunk, because one lookup builds all 1 024 of them.
   *
   * `ChunkHeightSampler.heights` evaluates every column in the chunk and every vector feature reaching it, so
   * asking it for one column costs the same as asking for the whole block. That was tolerable while the only
   * callers were manifest building and the occasional teleport; it is not now that [surfaceElevationAt] is on
   * `MoveSystem`'s per-step path, where a party walking together would rebuild the same block once per entity
   * per tile.
   */
  private val columnHeights = Lru<Pair<Int, Int>, ColumnHeights>(settings.hotChunkCapacity)

  private fun heightsOf(chunkX: Int, chunkY: Int): ColumnHeights =
    columnHeights.getOrPut(chunkX to chunkY) {
      loaded.columns.heights(ChunkPos(chunkX, chunkY, 0), 0)
    }

  private fun computeSurfaceSlabs(column: ChunkPos): IntArray {
    val heights = heightsOf(column.x, column.y)
    val seaSlabs = ChunkCoords.seaSurfaceSlabs(loaded.config)

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
      LOG.warn { "Column heights for $column are not finite; falling back to the sea-surface slabs" }
      return seaSlabs.sorted().toIntArray()
    }

    // A sorted set rather than arithmetic on the ranges, because the water slabs and cave passages may fall
    // inside the terrain span, immediately above or below it, or hundreds of metres away, and the fast path is
    // the one where everything coincides and collapses to a single entry.
    val found = sortedSetOf<Int>().apply { addAll(seaSlabs) }
    for (z in loaded.config.chunkZOf(lowest)..loaded.config.chunkZOf(highest)) {
      found.add(z)
    }

    // GeneratedWorld.contentSlabsOf also unions in intersecting cave passages, which the terrain heightfield
    // span above cannot see at all - without this, a cave below the surface slab is generated, cached, and
    // never subscribed to, so a player walking into a passage finds the ground in front of them does not exist.
    for (z in worldService.generated.contentSlabsOf(column.x, column.y)) {
      found.add(z)
    }

    return found.toIntArray()
  }

  /** The authoritative merged chunk: base with every edit applied, or the baked blob. */
  /**
   * Ground elevation in metres at a world voxel column, straight from the heightfield.
   *
   * Read from the *heightfield* rather than by scanning voxels, which is what makes it affordable: it is a
   * raster sample plus a feature query, not a chunk generation. It therefore reports the top of the **base
   * terrain** and knows nothing about player edits - fine for putting somebody on the ground, wrong for
   * anything that has to agree with what the player can walk on. Use [derived] for that.
   *
   * Sea level is not applied here. A submarine column reports its seabed, and it is the caller's business to
   * decide whether standing there makes sense.
   */
  fun surfaceElevationAt(voxelX: Long, voxelY: Long): Double? {
    val localised = ChunkCoords.localise(loaded.config, voxelX, voxelY, 0) ?: return null
    val column = normalise(localised.chunk)

    return heightsOf(column.x, column.y)[localised.localX, localised.localY]
  }

  fun merged(chunk: ChunkPos): VoxelChunk = loaded.store.merged(chunk)

  /**
   * The generated macro navigation graph, straight from the world tier.
   *
   * Exposed here rather than reached through `WorldService` by the navigation package for the same reason
   * `derived()` and [surfaceElevationAt] are: this class is the one place that knows the world is loaded and
   * owns everything derived from it, so a second door onto the same data is a second thing to get the boot
   * ordering wrong with.
   */
  fun navGraphSource(): NavGraph = worldService.generated.world.navGraph

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
   * Removes rock in the shape of [brush], bumping each affected chunk's revision and queueing the change.
   *
   * The single entry point for terrain mutation, and deliberately the only place that touches
   * `ChunkStore.carve` - so no caller can change the world without the revision moving and the subscribers
   * being told, which are the two things that would otherwise silently desynchronise every client holding the
   * chunk.
   *
   * ### One brush, one pass, however many chunks it reaches
   *
   * A brush is a shape in *world* space, so unlike the per-voxel entry point this replaces it can straddle
   * chunk borders and the world seam. Each voxel is localised and its chunk normalised - a coordinate east of
   * the eastern edge is a real place, the same place as one in the west, and the generator has to be asked
   * about it under the name it knows. Two different addresses can therefore normalise to the same chunk, which
   * is why removals are accumulated into a map per normalised chunk before anything is applied.
   *
   * Each chunk is then carved **once**, with its whole batch. Carving per voxel would bump the revision
   * seventy times for one swing, queue seventy patches, and invalidate the derived structures seventy times.
   *
   * ### What the brush offers is a ceiling, not a subtraction
   *
   * A voxel the brush half covers is offered `Occupancy.of(1 - removed)`: *at most this much may remain*. It
   * is not "take this fraction of what is left", and the difference matters twice over. Applying the same
   * brush twice is then a no-op rather than eroding the voxel a second time, and two overlapping brushes
   * compose by taking the lower ceiling regardless of the order they arrive in. It is also the rule
   * `ChunkMaterializer.carve` already uses for generated voids - `if (reduced < current)` - so a shaft the
   * generator cut and a shaft a player dug are computed the same way.
   *
   * @return every voxel that actually changed, with what was there before, for whatever turns rock into items
   */
  fun carve(brush: CarveBrush): CarveResult {
    val config = loaded.config

    /** One voxel the brush wants, before the store has been asked whether there is anything there to take. */
    class Offer(
      val index: Int,
      val voxelX: Long,
      val voxelY: Long,
      val voxelZ: Int,
      val ceiling: Int
    )

    // Grouped by *normalised* chunk, because two brush voxels either side of the world seam are two addresses
    // for one chunk and must not become two batches that overwrite each other's revision. Within a chunk,
    // keyed by voxel index so that pair collapses to one offer at the lower ceiling.
    val batches = LinkedHashMap<ChunkPos, LinkedHashMap<Int, Offer>>()

    brush.forEachVoxel { voxelX, voxelY, voxelZ, removed ->
      val localised = ChunkCoords.localise(config, voxelX, voxelY, voxelZ.toLong())
      if (localised != null) {
        val chunk = normalise(localised.chunk)
        val index = ChunkCoords.voxelIndex(config, localised.localX, localised.localY, localised.localZ)
        val offer = Offer(index, voxelX, voxelY, voxelZ, Occupancy.of(1.0 - removed))

        batches.getOrPut(chunk) { LinkedHashMap() }
          .merge(index, offer) { held, incoming -> if (incoming.ceiling < held.ceiling) incoming else held }
      }
    }

    if (batches.isEmpty()) return CarveResult.NOTHING

    val carved = ArrayList<CarvedVoxel>()
    val touched = LinkedHashSet<ChunkPos>()

    for ((chunk, offers) in batches) {
      // Read before carving: a removal records how much is left, never what it was, so this is the only
      // moment the prior material is still knowable without regenerating the base.
      val before = loaded.store.merged(chunk)

      val effective = ArrayList<Int>(offers.size)
      val prior = ArrayList<CarvedVoxel>(offers.size)

      for (offer in offers.values) {
        val held = before.occupancy[offer.index].toInt() and 0xFF
        if (offer.ceiling >= held) continue

        // Bedrock-like materials and the wall between a gallery and a lake. See CarveRules for why a breach is
        // refused rather than flooded: there is no runtime fluid state to flood with, and no way to seal it.
        if (!CarveRules.mayCarve(before, offer.index)) continue

        effective.add(ChunkDelta.pack(offer.index, offer.ceiling))
        prior.add(
          CarvedVoxel(
            chunk = chunk,
            voxelX = offer.voxelX,
            voxelY = offer.voxelY,
            voxelZ = offer.voxelZ,
            priorBlock = BlockType.of(before.blocks[offer.index].toInt() and 0xFF),
            priorOccupancy = held,
            remainingOccupancy = offer.ceiling
          )
        )
      }

      if (effective.isEmpty()) continue

      val from = revisionOf(chunk)
      val removals = effective.sorted().toIntArray()
      val outcome = loaded.store.carve(chunk, removals)
      if (outcome.changed == 0) continue

      val queued = pending.getOrPut(chunk) { LinkedHashMap() }
      for (entry in removals) {
        queued.merge(ChunkDelta.indexOf(entry), ChunkDelta.remainingOf(entry), ::minOf)
      }
      pendingFrom.putIfAbsent(chunk, from)
      if (outcome.baked) pendingBaked.add(chunk)

      carved.addAll(prior)
      touched.add(chunk)

      LOG.debug {
        "Carved ${outcome.changed} voxels from $chunk, revision $from -> ${revisionOf(chunk)}" +
            if (outcome.baked) " (baked)" else ""
      }
    }

    return if (carved.isEmpty()) CarveResult.NOTHING else CarveResult(carved, touched)
  }

  /**
   * Called by [ChunkStore] whenever a chunk's contents change.
   *
   * Bumps the revision and marks the derived structures stale. The rebuild itself is *not* done here - it is
   * queued and paid for out of a per-tick budget, so one swing of a pick cannot cost the zone thread a
   * walkability rebuild in the middle of a tick.
   */
  private fun onChunkChanged(chunk: ChunkPos) {
    revisions[chunk] = revisionOf(chunk) + 1
    loaded.derived.invalidate(chunk)
    for (listener in changeListeners) listener(chunk)
  }

  /**
   * Registered callbacks for "this chunk's contents changed".
   *
   * A list rather than a single slot because the two existing reactions are already unrelated - the derived
   * structures rebuild, and the subscribers get told - and the navigation graph's is a third: a bridge somebody
   * mined out from under is a chunk change, and the macro edges over it have to be re-tested. Kept as a plain
   * list, called synchronously, because everything here is on the tick thread by construction.
   */
  private val changeListeners = ArrayList<(ChunkPos) -> Unit>()

  /**
   * Registers a callback fired whenever a chunk's contents change.
   *
   * The listener runs on the tick thread inside the carve, so it must be cheap and must not itself carve: mark
   * something stale and return. `MacroGraphService` queues an edge index; it does not re-test the edge there.
   */
  fun onChunkChanged(handler: (ChunkPos) -> Unit) {
    changeListeners.add(handler)
  }

  /**
   * Hands over every change since the last call and forgets them.
   *
   * Drained once per tick by [ChunkStreamSystem]. Coalescing happens on the way in - the pending map is keyed
   * by voxel index and keeps the lowest occupancy - so a player holding a dig key down produces one removal
   * per voxel per tick rather than one message per swing, and a voxel worked twice in a tick is described by
   * where it ended up.
   */
  fun drainChanges(): List<ChunkChange> {
    if (pending.isEmpty()) return emptyList()

    val changes = pending.map { (chunk, removals) ->
      ChunkChange(
        chunk = chunk,
        removals = removals.entries
          .map { (index, remaining) -> ChunkDelta.pack(index, remaining) }
          .sorted()
          .toIntArray(),
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
