package net.bestia.worldgen.derived

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * Which voxels of one chunk stop a line of sight: one bit per voxel, in the voxel layout (`(y * size + x) * height + z`),
 * plus the chunk's [StructureCells] for the walls on cell borders.
 *
 * Built for the per-attack line-of-sight check, which runs on every shot and every spell and so has to be a handful
 * of array reads. The [OpacityGrid] beside it is four voxels to a cell, and at that resolution a cell holding the
 * ground under an entity's feet reads as half full and blinds everybody standing on a slope; one bit per voxel does
 * not, and costs 32 KB for a 64³ chunk.
 *
 * A voxel blocks when its block is solid and at least [OPAQUE_FROM] full. The surface voxel an entity stands in is
 * usually partly full (the terrain is continuous, see `Occupancy`), and counting it would put a wall at everybody's
 * ankles. A built cell blocks when its fill does the same; its walls are checked separately by [wallsAt], because a
 * wall stands on a border and blocks only a line that crosses that border.
 *
 * Immutable, so it can be built on a worker thread and handed to the store's owner.
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
