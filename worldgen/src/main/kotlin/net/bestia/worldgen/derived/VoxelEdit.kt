package net.bestia.worldgen.derived

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * One voxel's whole new state, packed as `(voxelIndex shl 16) or (blockId shl 8) or occupancy` in a `Long`.
 *
 * The index sits in the high bits, so natural `Long` order is index order and a sorted batch needs no comparator.
 */
object VoxelEdit {

  /** Refuses air with material and material without any, so a stored edit can never break that invariant. */
  fun pack(voxelIndex: Int, block: BlockType, occupancy: Int): Long {
    require(voxelIndex >= 0) { "Voxel index $voxelIndex is negative" }
    require(occupancy in Occupancy.EMPTY..Occupancy.FULL) { "Occupancy must fit a byte, was $occupancy" }
    require((block == BlockType.AIR) == (occupancy == Occupancy.EMPTY)) {
      "$block at occupancy $occupancy: air is always empty, and nothing else ever is"
    }

    return (voxelIndex.toLong() shl 16) or (block.id.toLong() shl 8) or occupancy.toLong()
  }

  /** What [voxels] holds at [voxelIndex] right now. */
  fun of(voxels: VoxelChunk, voxelIndex: Int): Long {
    val block = BlockType.of(voxels.blocks[voxelIndex].toInt() and 0xFF)

    return pack(voxelIndex, block, Occupancy.unsigned(voxels.occupancy[voxelIndex]))
  }

  fun indexOf(edit: Long): Int {
    return (edit ushr 16).toInt()
  }

  fun blockOf(edit: Long): BlockType {
    return BlockType.of(blockIdOf(edit))
  }

  fun blockIdOf(edit: Long): Int {
    return ((edit ushr 8) and 0xFF).toInt()
  }

  fun occupancyOf(edit: Long): Int {
    return (edit and 0xFF).toInt()
  }
}
