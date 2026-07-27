package net.bestia.worldgen.derived

import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.worldgen.voxel.VoxelChunk
import net.bestia.worldgen.core.ChunkPos
import kotlin.math.ceil
import kotlin.math.max

/**
 * Per-column summaries of a chunk: the top of the column, the water in it, and whether anything is
 * sheltered underneath.
 *
 * The cheapest of the derived structures and the one queried most. "How deep is the water here" decides
 * swimming and drowning; "is this spot under a roof" decides whether rain and sunlight reach it; "what is
 * the top of this column" is wanted by every spawn, every projectile and every UI hint.
 *
 * All three come out of one downward pass per column instead of a raycast per query, which is the design
 * rule for this whole package: hot-path queries never touch voxels.
 *
 * ### Heights are continuous
 *
 * Each of the three is stored as a height in **voxel units above the chunk floor**, not as a voxel index:
 * `39.3`, not `39`. Voxels carry an occupancy fraction, so the information is there, and the callers all
 * want the continuous value - a spawn point half a metre inside the ground and a swimming check that
 * disagrees with the visible waterline are both worse than an extra `ceil`. [voxelOf] converts back for the
 * callers that genuinely need an index, using the same rule the materializer used to write it.
 */
class ColumnSummary(
  val chunk: ChunkPos,
  val size: Int,
  /** Top of the highest solid material per column in voxel units, or -1.0 for a column with none. */
  private val surfaceHeight: DoubleArray,
  /** Water surface per column in voxel units, or -1.0 where there is no water. */
  private val waterHeight: DoubleArray,
  /**
   * Top of the highest solid material that lies *below* an air gap, or -1.0 where there is none.
   *
   * In other words: the floor you would be standing on if you were underneath something. A column of open
   * terrain has no such floor; a column inside a building, a cave or under a bridge does.
   */
  private val shelteredFloorHeight: DoubleArray
) {

  private fun index(localX: Int, localY: Int) = localY * size + localX

  /** Top of the column's solid material, roof included, in voxel units. -1.0 for pure air and water. */
  fun surfaceHeightAt(localX: Int, localY: Int) = surfaceHeight[index(localX, localY)]

  /** Local z of the voxel the solid surface lives in, or -1 for a column with none. */
  fun surfaceAt(localX: Int, localY: Int) = voxelOf(surfaceHeight[index(localX, localY)])

  /** Water surface in voxel units, or -1.0 where the column is dry. */
  fun waterHeightAt(localX: Int, localY: Int) = waterHeight[index(localX, localY)]

  /** Local z of the voxel the water surface lives in, or -1 where dry. */
  fun waterAt(localX: Int, localY: Int) = voxelOf(waterHeight[index(localX, localY)])

  /**
   * Depth of standing water over the column's solid top, in voxels. Zero where dry.
   *
   * A column with water but no solid material at all is measured from the chunk floor, because that is where
   * the water starts as far as this chunk can tell.
   */
  fun waterDepthAt(localX: Int, localY: Int): Double {
    val i = index(localX, localY)
    if (waterHeight[i] < 0.0) return 0.0
    return (waterHeight[i] - max(surfaceHeight[i], 0.0)).coerceAtLeast(0.0)
  }

  /**
   * True when there is a floor under an overhang: a building interior, a cave, the underside of a bridge.
   *
   * What makes "is it raining on this NPC" free. Answering it by raycast per NPC per tick is exactly the
   * kind of query that quietly consumes a zone thread.
   */
  fun isSheltered(localX: Int, localY: Int) = shelteredFloorHeight[index(localX, localY)] >= 0.0

  fun shelteredFloorHeightAt(localX: Int, localY: Int) = shelteredFloorHeight[index(localX, localY)]

  fun shelteredFloorAt(localX: Int, localY: Int) = voxelOf(shelteredFloorHeight[index(localX, localY)])

  override fun toString() = "ColumnSummary[$chunk]"

  companion object {

    /**
     * Which voxel a height in voxel units lives in, or -1 for the "nothing here" sentinel.
     *
     * A height landing exactly on a voxel boundary belongs to the voxel below it - the same rule the
     * materializer writes with, so a full voxel 39 reads back as 39 rather than as an empty 40.
     */
    fun voxelOf(height: Double): Int = if (height < 0.0) -1 else ceil(height).toInt() - 1

    fun of(voxels: VoxelChunk): ColumnSummary {
      val size = voxels.size
      val surface = DoubleArray(size * size) { -1.0 }
      val water = DoubleArray(size * size) { -1.0 }
      val sheltered = DoubleArray(size * size) { -1.0 }

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val i = localY * size + localX
          val offset = voxels.columnOffset(localX, localY)

          var topSolid = -1.0
          var gapBelowTop = false

          for (z in voxels.height - 1 downTo 0) {
            val block = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF) ?: continue
            val top = z + Occupancy.fractionOf(voxels.occupancy[offset + z].toInt() and 0xFF)

            if (block == BlockType.WATER || block == BlockType.ICE) {
              if (water[i] < 0.0) water[i] = top
            }

            // Ice is solid but it is the surface of water, not a roof over anything, so it does not count
            // as structure for either of the other two answers.
            val structural = block.solid && block != BlockType.ICE

            when {
              structural && topSolid < 0.0 -> topSolid = top

              // The first structural block below a gap is the sheltered floor. Nothing lower matters:
              // deeper gaps are caves under caves, and the shallowest one is the one you stand in.
              structural && gapBelowTop -> {
                sheltered[i] = top
                break
              }

              !structural && topSolid >= 0.0 -> gapBelowTop = true
            }
          }

          surface[i] = topSolid
        }
      }

      return ColumnSummary(voxels.chunk, size, surface, water, sheltered)
    }
  }
}
