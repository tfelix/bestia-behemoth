package net.bestia.worldgen.voxel

import net.bestia.worldgen.core.ChunkPos

/**
 * A materialised chunk: `size x size x height` blocks.
 *
 * Laid out with the vertical axis contiguous, so one voxel column is one contiguous span of the array.
 * That is the layout both consumers want. Run-length encoding compresses along it, because terrain runs
 * are overwhelmingly vertical - a column is typically a few hundred of one rock, some soil, and air -
 * and every derived structure that asks "what is the highest solid block here" walks exactly one span.
 */
class VoxelChunk(
  val chunk: ChunkPos,
  val size: Int,
  val height: Int,
  val blocks: ByteArray
) {

  init {
    require(size > 0 && height > 0) { "Chunk dimensions must be positive, was ${size}x${size}x$height" }
    require(blocks.size == size * size * height) {
      "A ${size}x${size}x$height chunk needs ${size * size * height} blocks, got ${blocks.size}"
    }
  }

  constructor(chunk: ChunkPos, size: Int, height: Int) :
      this(chunk, size, height, ByteArray(size * size * height))

  val volume get() = blocks.size

  /** Start of the voxel column at ([localX], [localY]) in [blocks]. */
  fun columnOffset(localX: Int, localY: Int) = (localY * size + localX) * height

  fun index(localX: Int, localY: Int, localZ: Int) = columnOffset(localX, localY) + localZ

  operator fun get(localX: Int, localY: Int, localZ: Int): BlockType =
    BlockType.of(blocks[index(localX, localY, localZ)].toInt() and 0xFF)

  operator fun set(localX: Int, localY: Int, localZ: Int, block: BlockType) {
    blocks[index(localX, localY, localZ)] = block.id.toByte()
  }

  /** Raw block id, for hot loops that do not want the enum lookup. */
  fun rawAt(localX: Int, localY: Int, localZ: Int): Int =
    blocks[index(localX, localY, localZ)].toInt() and 0xFF

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

  fun countOf(block: BlockType): Int {
    val id = block.id.toByte()
    var count = 0
    for (b in blocks) {
      if (b == id) count++
    }
    return count
  }

  fun copy() = VoxelChunk(chunk, size, height, blocks.copyOf())

  override fun toString() = "VoxelChunk[$chunk, ${size}x${size}x$height]"

  private companion object {
    val AIR_ID = BlockType.AIR.id.toByte()
  }
}
