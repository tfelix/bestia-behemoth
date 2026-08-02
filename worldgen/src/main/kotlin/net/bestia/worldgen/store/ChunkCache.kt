package net.bestia.worldgen.store

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * A place blobs can be kept by key: node-local disk, or a shared object store.
 *
 * An interface rather than an implementation because everything in this module is meant to stay free of
 * I/O - that is what lets the whole pipeline run offline in a viewer and in a unit test. The filesystem
 * and object-store implementations belong with the service that owns them.
 */
interface ChunkBlobStore {
  fun get(key: Long): ByteArray?
  fun put(key: Long, blob: ByteArray)
  fun remove(key: Long) {}
}

/** An in-memory blob store, for tests and for a single-node world. */
class MemoryBlobStore : ChunkBlobStore {
  private val blobs = LinkedHashMap<Long, ByteArray>()

  val size get() = blobs.size

  override fun get(key: Long): ByteArray? = blobs[key]
  override fun put(key: Long, blob: ByteArray) {
    blobs[key] = blob
  }

  override fun remove(key: Long) {
    blobs.remove(key)
  }
}

/**
 * Cache keys for generated chunks.
 *
 * The key folds in the world seed, the *whole* pipeline version vector, and the coordinate. Including the
 * version vector is what makes cache invalidation correct by construction: retune erosion and every chunk
 * key changes, so nothing stale can be served, and no explicit eviction pass is needed. Retune nothing and
 * the keys are stable forever, which is what lets a cold store accumulate a world over months.
 */
object ChunkKey {

  fun of(seed: Long, pipelineVersion: Long, chunk: ChunkPos): Long =
    GenRng.hash(seed, pipelineVersion, chunk.key())
}

/**
 * Three-tier cache in front of chunk generation: in-process, then whatever stores are chained behind it.
 *
 * The tiers exist because the costs are three orders of magnitude apart. Generating a base chunk means
 * lifting the raster, evaluating every vector feature that reaches it, and materialising a quarter of a
 * million voxels - milliseconds. Decoding an RLE blob is microseconds. Reading a cached object is
 * whatever the network costs. So the thing worth optimising is never having to generate twice.
 *
 * A miss at every tier generates, then writes back to *all* of them, so the next reader on any node is
 * warm. Writes go outermost-first so that a crash between writes leaves a cold store that is behind rather
 * than a hot cache that is ahead of it.
 */
class ChunkCache(
  private val seed: Long,
  /** Readable because `ChunkStore` keys baked blobs on it too - see `ChunkStore.bakedKeyOf`. */
  val pipelineVersion: Long,
  /** Generates a base chunk from scratch. Must be a pure function of the coordinate. */
  private val generate: (ChunkPos) -> VoxelChunk,
  /** Warm to cold, nearest first. May be empty, which makes this a plain in-process cache. */
  private val tiers: List<ChunkBlobStore> = emptyList(),
  private val hotCapacity: Int = DEFAULT_HOT_CAPACITY
) {

  /** Access-ordered, so eviction drops the chunks nobody is standing near. */
  private val hot = object : LinkedHashMap<Long, VoxelChunk>(64, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, VoxelChunk>) =
      size > hotCapacity
  }

  var hits = 0
    private set
  var blobHits = 0
    private set
  var generated = 0
    private set

  val hotSize get() = hot.size

  fun keyOf(chunk: ChunkPos) = ChunkKey.of(seed, pipelineVersion, chunk)

  /** The generated base for a chunk, from the nearest tier that has it. */
  fun base(chunk: ChunkPos): VoxelChunk {
    val key = keyOf(chunk)

    hot[key]?.let {
      hits++
      return it
    }

    for (tier in tiers) {
      val blob = tier.get(key) ?: continue
      blobHits++
      val decoded = RleCodec.decode(chunk, blob)
      hot[key] = decoded
      // Promote into the tiers nearer than the one that had it, so a second reader does not go as far.
      for (nearer in tiers) {
        if (nearer === tier) break
        nearer.put(key, blob)
      }
      return decoded
    }

    generated++
    val fresh = generate(chunk)
    val blob = RleCodec.encode(fresh)
    for (tier in tiers.asReversed()) {
      tier.put(key, blob)
    }
    hot[key] = fresh

    return fresh
  }

  /** Drops a chunk from every tier. For baking, which replaces the generated base with a stored one. */
  fun evict(chunk: ChunkPos) {
    val key = keyOf(chunk)
    hot.remove(key)
    for (tier in tiers) tier.remove(key)
  }

  fun resetStatistics() {
    hits = 0
    blobHits = 0
    generated = 0
  }

  override fun toString() =
    "ChunkCache[hot ${hot.size}/$hotCapacity, $hits hits, $blobHits blob hits, $generated generated]"

  companion object {
    /** Roughly the chunks within sight of a handful of players. */
    const val DEFAULT_HOT_CAPACITY = 512
  }
}
