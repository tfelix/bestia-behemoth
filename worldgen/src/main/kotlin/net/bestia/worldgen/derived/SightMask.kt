package net.bestia.worldgen.derived

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * Which voxels of one chunk stop a line of sight, one bit each (32 KB for a 64³ chunk), plus the structure walls on
 * cell borders ([wallsAt]). Per voxel rather than [OpacityGrid]'s four: at that size the ground under an entity's
 * feet reads as a wall. A voxel blocks from [OPAQUE_FROM] full, so a partly filled surface voxel does not.
 */
class SightMask private constructor(
  val size: Int,
  val height: Int,
  private val bits: LongArray,
  private val structures: StructureCells?
) {

  /** Whether the voxel at these local coordinates stops sight. Out of the chunk reads as clear. */
  fun blocks(localX: Int, localY: Int, localZ: Int): Boolean {
    if (localX < 0 || localY < 0 || localZ < 0 || localX >= size || localY >= size || localZ >= height) return false
    val index = (localY * size + localX) * height + localZ

    return bits[index ushr 6] and (1L shl index) != 0L
  }

  /** The [Edges] walls of a built cell, [Edges.NONE] where nothing is built. */
  fun wallsAt(localX: Int, localY: Int, localZ: Int): Int {
    val cells = structures ?: return Edges.NONE
    if (!cells.hasColumn(localX, localY)) return Edges.NONE

    return cells.edgesAt(localX, localY, localZ)
  }

  companion object {

    /** Fill from which a voxel or a built cell counts as opaque. */
    const val OPAQUE_FROM = Occupancy.FULL / 2

    fun of(voxels: VoxelChunk, structures: StructureCells? = null): SightMask {
      val bits = LongArray((voxels.volume + 63) / 64)

      for (index in 0 until voxels.volume) {
        val block = BlockType.ofOrNull(voxels.blocks[index].toInt() and 0xFF) ?: continue
        if (!block.solid) continue
        if (Occupancy.unsigned(voxels.occupancy[index]) < OPAQUE_FROM) continue

        bits[index ushr 6] = bits[index ushr 6] or (1L shl index)
      }

      if (structures != null && structures.cellCount > 0) {
        for (localY in 0 until voxels.size) {
          for (localX in 0 until voxels.size) {
            if (!structures.hasColumn(localX, localY)) continue
            val offset = voxels.columnOffset(localX, localY)
            for (localZ in 0 until voxels.height) {
              if (structures.fillAt(localX, localY, localZ) < OPAQUE_FROM) continue
              val index = offset + localZ
              bits[index ushr 6] = bits[index ushr 6] or (1L shl index)
            }
          }
        }
      }

      return SightMask(voxels.size, voxels.height, bits, structures)
    }
  }
}
