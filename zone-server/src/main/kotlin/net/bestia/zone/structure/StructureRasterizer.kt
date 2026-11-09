package net.bestia.zone.structure

import net.bestia.worldgen.derived.Edges
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.geometry.Vec3L
import kotlin.math.PI
import kotlin.math.roundToInt

/** Turns a placed piece into the cells it fills in the world. */
object StructureRasterizer {

  /**
   * The cells of [piece] placed at [origin] and turned by [quarterTurns] the way the client turns a model by the same
   * yaw: one turn takes x+ to y-.
   *
   * A wall is also written into the cell on the other side of its border, so whoever reads a column finds every
   * wall around it without looking at the neighbour.
   */
  fun rasterize(piece: StructurePiece, origin: Vec3L, quarterTurns: Int): List<StructureCell> {
    val cells = ArrayList<StructureCell>(piece.cells.size * 2)

    for (local in piece.cells) {
      val (dx, dy) = turned(local.x, local.y, quarterTurns)
      val x = origin.x + dx
      val y = origin.y + dy
      val z = origin.z + local.z
      val edges = Edges.rotated(local.edges, -quarterTurns)

      cells.add(StructureCell(x, y, z, local.fill, edges))
      for (side in SIDES) {
        if (edges and side == Edges.NONE) continue

        val (stepX, stepY) = stepThrough(side)
        cells.add(StructureCell(x + stepX, y + stepY, z, Occupancy.EMPTY, Edges.opposite(side)))
      }
    }

    return cells
  }

  /** The nearest quarter turn to a yaw in radians. */
  fun quarterTurnsOf(yaw: Float): Int {
    return Math.floorMod((yaw / (PI / 2)).roundToInt(), 4)
  }

  /** [yaw] turned to the quarter turn it stands at, so a piece is drawn the way it blocks. */
  fun snappedYaw(yaw: Float): Float {
    return (quarterTurnsOf(yaw) * PI / 2).toFloat()
  }

  private fun turned(x: Int, y: Int, quarterTurns: Int): Pair<Int, Int> {
    return when (Math.floorMod(quarterTurns, 4)) {
      0 -> x to y
      1 -> y to -x
      2 -> -x to -y
      else -> -y to x
    }
  }

  private fun stepThrough(side: Int): Pair<Int, Int> {
    return when (side) {
      Edges.X_PLUS -> 1 to 0
      Edges.Y_PLUS -> 0 to 1
      Edges.X_MINUS -> -1 to 0
      else -> 0 to -1
    }
  }

  private val SIDES = intArrayOf(Edges.X_PLUS, Edges.Y_PLUS, Edges.X_MINUS, Edges.Y_MINUS)
}
