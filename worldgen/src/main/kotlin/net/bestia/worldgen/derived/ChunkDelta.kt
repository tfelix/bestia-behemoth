package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.RleCodec
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * The player edits layered over one generated chunk.
 *
 * The generated world is a base layer and player changes are a sparse delta on top of it, which is what
 * makes a large world storable at all: an untouched chunk is stored as nothing, and a chunk read is
 * `base(generated, cached) ⊕ delta(persistent)`.
 *
 * Stored as a flat position-to-block map rather than as a list of edits. A player who breaks and replaces
 * the same block a hundred times should cost one entry, not a hundred, and the map also makes the
 * "fraction of the chunk modified" figure that drives baking a simple size comparison.
 *
 * Not thread safe. One delta belongs to one chunk, and a chunk belongs to one owning node.
 */
class ChunkDelta(
  val chunk: ChunkPos,
  val size: Int,
  val height: Int
) {

  /** Voxel index within the chunk to the block now there. */
  private val edits = LinkedHashMap<Int, Byte>()

  val editCount get() = edits.size

  val isEmpty get() = edits.isEmpty()

  val volume get() = size * size * height

  /** Fraction of the chunk's voxels that have been edited. */
  val coverage get() = editCount.toDouble() / volume

  fun index(localX: Int, localY: Int, localZ: Int) = (localY * size + localX) * height + localZ

  fun set(localX: Int, localY: Int, localZ: Int, block: BlockType) {
    require(localX in 0 until size && localY in 0 until size && localZ in 0 until height) {
      "($localX,$localY,$localZ) is outside a ${size}x${size}x$height chunk"
    }
    edits[index(localX, localY, localZ)] = block.id.toByte()
  }

  fun get(localX: Int, localY: Int, localZ: Int): BlockType? =
    edits[index(localX, localY, localZ)]?.let { BlockType.of(it.toInt() and 0xFF) }

  /** Forgets an edit, so the base shows through again. */
  fun clear(localX: Int, localY: Int, localZ: Int) {
    edits.remove(index(localX, localY, localZ))
  }

  /**
   * Applies this delta onto a copy of [base] and returns the merged chunk.
   *
   * The server has to hold merged voxel state - it is not a design choice. Line of sight, projectile
   * collision, movement validation and NPC pathing all need the server's own view of geometry, and a
   * server that cannot answer "is there a wall between these two players" will have players build walls
   * specifically to find out what happens.
   *
   * The merge itself is deliberately cheap and not worth optimising: overlaying even a hundred thousand
   * edits onto a decoded base is memcpy-scale. The expensive part is *regenerating* the base, which is what
   * [net.bestia.worldgen.store.ChunkCache] exists for.
   */
  fun mergedOnto(base: VoxelChunk): VoxelChunk {
    require(base.chunk == chunk) { "delta for $chunk applied to ${base.chunk}" }
    require(base.size == size && base.height == height) {
      "delta is ${size}x${size}x$height, base is ${base.size}x${base.size}x${base.height}"
    }

    val merged = base.copy()
    for ((position, block) in edits) {
      merged.blocks[position] = block
    }
    return merged
  }

  /** The columns this delta touches, so a derived structure can rebuild only what changed. */
  fun touchedColumns(): Set<Int> {
    val columns = HashSet<Int>()
    for (position in edits.keys) {
      columns.add(position / height)
    }
    return columns
  }

  /**
   * Whether this delta has grown past the point where storing it as a delta is still a saving.
   *
   * Delta compaction is mandatory, not optional. Deltas are unbounded - a player who terraforms a hillside
   * over six months accumulates a delta larger than the chunk itself - and past a threshold it is cheaper
   * in both space and time to bake the merged result as the new base, drop the delta, and skip generation
   * entirely on future reads. Heavily built areas then become *cheaper* rather than merely smaller.
   *
   * @param mergedEncodedBytes size of the merged chunk when RLE encoded, for the second test
   */
  fun shouldBake(mergedEncodedBytes: Int): Boolean =
    coverage >= BAKE_COVERAGE || estimatedBytes() >= mergedEncodedBytes

  /** Rough wire size of this delta: a varint position and a block id per edit. */
  fun estimatedBytes(): Int = editCount * BYTES_PER_EDIT

  override fun toString() =
    "ChunkDelta[$chunk, $editCount edits, ${"%.1f".format(coverage * 100)}% of the chunk]"

  companion object {

    /** Fraction of a chunk's voxels beyond which it is baked rather than kept as a delta. */
    const val BAKE_COVERAGE = 0.30

    /** Position varint plus block id. Generous, so the size test errs towards baking. */
    private const val BYTES_PER_EDIT = 4

    /**
     * Bakes a delta into a new base: the merged chunk, RLE encoded, ready to store under the chunk
     * coordinate as `baked`.
     *
     * Also the migration path for a pipeline change. Once a world ships its pipeline version is frozen,
     * because any change shifts the base under the player's deltas. To upgrade: bake every chunk that has
     * a delta, then change the pipeline. Unmodified chunks regenerate against the new version harmlessly.
     */
    fun bake(base: VoxelChunk, delta: ChunkDelta): ByteArray =
      RleCodec.encode(delta.mergedOnto(base))
  }
}
