package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos

/**
 * A materialised chunk: `size x size x height` voxels, each a material and how much of the voxel it fills.
 *
 * Laid out with the vertical axis contiguous, so one voxel column is one contiguous span of the array.
 * That is the layout both consumers want. Run-length encoding compresses along it, because terrain runs
 * are overwhelmingly vertical - a column is typically a few hundred of one rock, some soil, and air -
 * and every derived structure that asks "what is the highest solid block here" walks exactly one span.
 *
 * ### Two parallel arrays, not interleaved pairs
 *
 * [blocks] and [occupancy] are separate arrays over the same index space. Interleaving them would break
 * every run in the encoder, because occupancy changes exactly where material does not: it is [Occupancy.FULL]
 * for every voxel below the air interface and [Occupancy.EMPTY] above it, so as its own stream it encodes to
 * a handful of runs per column while the material stream keeps the runs it always had. Splitting them also
 * means the many readers that only care about material - stratigraphy checks, block counts, ore queries -
 * are untouched by occupancy existing.
 *
 * ### The invariant
 *
 * Air has occupancy [Occupancy.EMPTY]; everything else has at least 1. So "is this voxel empty" can be
 * answered from either array alone and the two can never disagree. [validate] checks it over a whole chunk,
 * which is what [RleCodec.decode] does to a payload it did not produce itself.
 */
class VoxelChunk(
  val chunk: ChunkPos,
  val size: Int,
  val height: Int,
  val blocks: ByteArray,
  /** How much of each voxel its material fills, `0..255`. See [Occupancy]. */
  val occupancy: ByteArray
) {

  init {
    require(size > 0 && height > 0) { "Chunk dimensions must be positive, was ${size}x${size}x$height" }
    require(blocks.size == size * size * height) {
      "A ${size}x${size}x$height chunk needs ${size * size * height} blocks, got ${blocks.size}"
    }
    require(occupancy.size == blocks.size) {
      "Occupancy has ${occupancy.size} entries for ${blocks.size} blocks"
    }
  }

  constructor(chunk: ChunkPos, size: Int, height: Int) : this(
    chunk, size, height,
    ByteArray(size * size * height),
    ByteArray(size * size * height)
  )

  val volume get() = blocks.size

  /** Start of the voxel column at ([localX], [localY]) in [blocks] and [occupancy]. */
  fun columnOffset(localX: Int, localY: Int) = (localY * size + localX) * height

  fun index(localX: Int, localY: Int, localZ: Int) = columnOffset(localX, localY) + localZ

  operator fun get(localX: Int, localY: Int, localZ: Int): BlockType =
    BlockType.of(blocks[index(localX, localY, localZ)].toInt() and 0xFF)

  /** Writes a completely filled voxel, or air. The common case; partial fill is only ever at a surface. */
  operator fun set(localX: Int, localY: Int, localZ: Int, block: BlockType) {
    set(localX, localY, localZ, block, if (block == BlockType.AIR) Occupancy.EMPTY else Occupancy.FULL)
  }

  /**
   * Writes a voxel with an explicit fill.
   *
   * @param occupancy `0..255`; must be zero for [BlockType.AIR] and non-zero for anything else, because a
   *   half-present block of air and a completely absent block of stone are both meaningless.
   */
  fun set(localX: Int, localY: Int, localZ: Int, block: BlockType, occupancy: Int) {
    require(occupancy in 0..255) { "Occupancy must fit a byte, was $occupancy" }
    require((block == BlockType.AIR) == (occupancy == Occupancy.EMPTY)) {
      "Air must have occupancy 0 and everything else must not; got $block at $occupancy"
    }
    val i = index(localX, localY, localZ)
    blocks[i] = block.id.toByte()
    this.occupancy[i] = occupancy.toByte()
  }

  /** Raw block id, for hot loops that do not want the enum lookup. */
  fun rawAt(localX: Int, localY: Int, localZ: Int): Int =
    blocks[index(localX, localY, localZ)].toInt() and 0xFF

  /** Raw occupancy, `0..255`. */
  fun occupancyAt(localX: Int, localY: Int, localZ: Int): Int =
    occupancy[index(localX, localY, localZ)].toInt() and 0xFF

  /** How much of the voxel its material fills, in `[0,1]`. */
  fun fillAt(localX: Int, localY: Int, localZ: Int): Double =
    Occupancy.fractionOf(occupancyAt(localX, localY, localZ))

  /** Highest local z holding a solid block, or -1 when the column is entirely air and water. */
  fun highestSolid(localX: Int, localY: Int): Int {
    val offset = columnOffset(localX, localY)
    for (z in height - 1 downTo 0) {
      val block = BlockType.ofOrNull(blocks[offset + z].toInt() and 0xFF) ?: continue
      if (block.solid) return z
    }
    return -1
  }

  /** Highest local z holding anything that is not air. Water counts; the lake surface is a surface. */
  fun highestNonAir(localX: Int, localY: Int): Int {
    val offset = columnOffset(localX, localY)
    for (z in height - 1 downTo 0) {
      if (blocks[offset + z] != AIR_ID) return z
    }
    return -1
  }

  /**
   * Continuous height of the top of the solid material in this column, in voxels above the chunk floor.
   *
   * The whole reason occupancy exists: `39.3` rather than `39`. Returns -1.0 for a column with no solid
   * voxel, matching [highestSolid].
   */
  fun solidHeightAt(localX: Int, localY: Int): Double {
    val z = highestSolid(localX, localY)
    if (z < 0) return -1.0
    return z + fillAt(localX, localY, z)
  }

  fun countOf(block: BlockType): Int {
    val id = block.id.toByte()
    var count = 0
    for (b in blocks) {
      if (b == id) count++
    }
    return count
  }

  /**
   * Checks the air-occupancy invariant over the whole chunk.
   *
   * @throws IllegalStateException naming the first offending voxel
   */
  fun validate() {
    for (i in blocks.indices) {
      val air = blocks[i] == AIR_ID
      val empty = occupancy[i] == Occupancy.EMPTY_BYTE
      check(air == empty) {
        val id = blocks[i].toInt() and 0xFF
        "Chunk $chunk voxel $i has block id $id at occupancy ${occupancy[i].toInt() and 0xFF}; " +
            "air must be empty and everything else must not be"
      }
    }
  }

  fun copy() = VoxelChunk(chunk, size, height, blocks.copyOf(), occupancy.copyOf())

  override fun toString() = "VoxelChunk[$chunk, ${size}x${size}x$height]"

  private companion object {
    val AIR_ID = BlockType.AIR.id.toByte()
  }
}
