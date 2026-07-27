package net.bestia.worldgen.derived

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.VoxelChunk

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
 */
class ColumnSummary(
  val chunk: ChunkPos,
  val size: Int,
  /** Local z of the highest solid block per column, or -1 for a column with none. */
  private val surfaceZ: IntArray,
  /** Local z of the water surface per column, or -1 where there is no water. */
  private val waterZ: IntArray,
  /**
   * Local z of the highest solid block that lies *below* an air gap, or -1 where there is none.
   *
   * In other words: the floor you would be standing on if you were underneath something. A column of open
   * terrain has no such floor; a column inside a building, a cave or under a bridge does.
   */
  private val shelteredFloorZ: IntArray
) {

  private fun index(localX: Int, localY: Int) = localY * size + localX

  /** Highest solid block - the top of the column, roof included. -1 for pure air and water. */
  fun surfaceAt(localX: Int, localY: Int) = surfaceZ[index(localX, localY)]

  /** Water surface, or -1 where the column is dry. */
  fun waterAt(localX: Int, localY: Int) = waterZ[index(localX, localY)]

  /** Depth of standing water over the column's solid top, in voxels. Zero where dry. */
  fun waterDepthAt(localX: Int, localY: Int): Int {
    val i = index(localX, localY)
    if (waterZ[i] < 0) return 0
    return (waterZ[i] - surfaceZ[i]).coerceAtLeast(0)
  }

  /**
   * True when there is a floor under an overhang: a building interior, a cave, the underside of a bridge.
   *
   * What makes "is it raining on this NPC" free. Answering it by raycast per NPC per tick is exactly the
   * kind of query that quietly consumes a zone thread.
   */
  fun isSheltered(localX: Int, localY: Int) = shelteredFloorZ[index(localX, localY)] >= 0

  fun shelteredFloorAt(localX: Int, localY: Int) = shelteredFloorZ[index(localX, localY)]

  override fun toString() = "ColumnSummary[$chunk]"

  companion object {

    fun of(voxels: VoxelChunk): ColumnSummary {
      val size = voxels.size
      val surface = IntArray(size * size) { -1 }
      val water = IntArray(size * size) { -1 }
      val sheltered = IntArray(size * size) { -1 }

      for (localY in 0 until size) {
        for (localX in 0 until size) {
          val i = localY * size + localX
          val offset = voxels.columnOffset(localX, localY)

          var topSolid = -1
          var gapBelowTop = false

          for (z in voxels.height - 1 downTo 0) {
            val block = BlockType.ofOrNull(voxels.blocks[offset + z].toInt() and 0xFF) ?: continue

            if (block == BlockType.WATER || block == BlockType.ICE) {
              if (water[i] < 0) water[i] = z
            }

            // Ice is solid but it is the surface of water, not a roof over anything, so it does not count
            // as structure for either of the other two answers.
            val structural = block.solid && block != BlockType.ICE

            when {
              structural && topSolid < 0 -> topSolid = z

              // The first structural block below a gap is the sheltered floor. Nothing lower matters:
              // deeper gaps are caves under caves, and the shallowest one is the one you stand in.
              structural && gapBelowTop -> {
                sheltered[i] = z
                break
              }

              !structural && topSolid >= 0 -> gapBelowTop = true
            }
          }

          surface[i] = topSolid
        }
      }

      return ColumnSummary(voxels.chunk, size, surface, water, sheltered)
    }
  }
}
