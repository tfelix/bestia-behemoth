package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk

/**
 * A downsampled opacity field over one chunk, for line of sight.
 *
 * Raycasting through full-resolution voxels once per attack, per frame, for every combat pair is a cost
 * that grows with the square of the player count and dominates a combat tick long before it becomes
 * interesting. At a downsample factor of four this is a sixty-fourth of the data and the ray takes a
 * quarter of the steps.
 *
 * Stores an **opacity fraction** per cell rather than a boolean, and that choice is the whole reason this
 * is usable. A boolean forces a bad decision at the resolution boundary: "opaque if any voxel is" makes a
 * single fence post block four metres of sight line, while "opaque if most voxels are" lets players see
 * through a one-voxel wall - and the second failure is the one they will find and exploit within a day. A
 * fraction lets the caster accumulate along the ray, so a thin wall attenuates a little at every step it
 * is crossed and blocks reliably once the ray has passed through enough of it.
 *
 * You lose precision at edges. Players will occasionally notice. The alternative eats the combat tick.
 */
class OpacityGrid(
  val chunk: ChunkPos,
  /** Voxels per cell along each axis. */
  val factor: Int,
  val width: Int,
  val depth: Int,
  val height: Int,
  /** Opacity per cell as 0..255, in `(y, x, z)` order to match the voxel layout. */
  val cells: ByteArray
) {

  init {
    require(cells.size == width * depth * height) {
      "a ${width}x${depth}x$height grid needs ${width * depth * height} cells, got ${cells.size}"
    }
  }

  fun index(x: Int, y: Int, z: Int) = (y * width + x) * height + z

  fun contains(x: Int, y: Int, z: Int) =
    x in 0 until width && y in 0 until depth && z in 0 until height

  /** Opacity in `[0,1]`. Out of bounds reads as transparent, so a ray simply leaves the chunk. */
  fun opacityAt(x: Int, y: Int, z: Int): Double {
    if (!contains(x, y, z)) return 0.0
    return (cells[index(x, y, z)].toInt() and 0xFF) / 255.0
  }

  /**
   * Whether a cell is opaque enough to stop sight on its own.
   *
   * A convenience for callers that want a boolean after all. [ACCUMULATE_THRESHOLD] corresponds to about a
   * quarter of the cell being solid, which at a factor of four is a single voxel wall seen face on.
   */
  fun blocksSight(x: Int, y: Int, z: Int, threshold: Double = ACCUMULATE_THRESHOLD) =
    opacityAt(x, y, z) >= threshold

  override fun toString() = "OpacityGrid[$chunk, ${width}x${depth}x$height at 1:$factor]"

  companion object {

    /** Default downsample factor. Four is the doc's suggestion and a good balance in practice. */
    const val DEFAULT_FACTOR = 4

    /** Opacity at which one cell alone stops a sight line. */
    const val ACCUMULATE_THRESHOLD = 0.25

    fun of(voxels: VoxelChunk, factor: Int = DEFAULT_FACTOR): OpacityGrid {
      require(factor >= 1) { "factor must be at least 1, was $factor" }

      // Rounded up, so a chunk whose dimensions are not a multiple of the factor still covers its whole
      // volume - a partial cell at the edge simply averages over fewer voxels.
      val width = (voxels.size + factor - 1) / factor
      val depth = (voxels.size + factor - 1) / factor
      val height = (voxels.height + factor - 1) / factor
      val cells = ByteArray(width * depth * height)

      // Occupancy-weighted, not a count of opaque voxels. A voxel thirty percent full of stone occludes
      // thirty percent as much, which is the same argument as the fraction-not-boolean choice above, applied
      // one level down: having gone to the trouble of storing how full a voxel is, rounding it back to solid
      // or empty here would put the resolution cliff straight back.
      //
      // Solidity is the material test, and `BlockType.opacity` used to be. That property was a fraction so
      // that a leaf canopy could attenuate a sight line rather than either stopping it outright or not at all
      // - and leaves left the palette for props, taking the only material with a fractional opacity with
      // them. Every remaining material's opacity was exactly `if (solid) 1.0 else 0.0`, so it was deleted and
      // this reads `solid` instead: identical output, one fewer property to keep true.
      //
      // The grid still reports a fraction. That fraction was always occupancy, which is untouched.
      val filled = IntArray(cells.size)
      val totals = IntArray(cells.size)

      for (localY in 0 until voxels.size) {
        val cellY = localY / factor
        for (localX in 0 until voxels.size) {
          val cellX = localX / factor
          val offset = voxels.columnOffset(localX, localY)
          val column = (cellY * width + cellX) * height

          for (localZ in 0 until voxels.height) {
            val cell = column + localZ / factor
            totals[cell]++
            val block = BlockType.ofOrNull(voxels.blocks[offset + localZ].toInt() and 0xFF)
            if (block != null && block.solid) {
              filled[cell] += voxels.occupancy[offset + localZ].toInt() and 0xFF
            }
          }
        }
      }

      for (i in cells.indices) {
        cells[i] = if (totals[i] == 0) 0 else (filled[i] / totals[i]).toByte()
      }

      return OpacityGrid(voxels.chunk, factor, width, depth, height, cells)
    }
  }
}
