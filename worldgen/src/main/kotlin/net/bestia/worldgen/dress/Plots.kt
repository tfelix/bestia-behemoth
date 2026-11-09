package net.bestia.worldgen.dress

import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.cos
import kotlin.math.sin

/**
 * Geometry the world tier and the chunk tier must both compute and agree on, to the plot.
 *
 * A cemetery's named graves are markers the world tier places on the first plots; the plain headstones are filled
 * into the rest by the chunk tier. Both walk this one grid, so a chunk never puts a plain stone on a named grave's
 * plot. The same goes for an oriented rectangle's corners, which the dressing stage writes as a ring and the
 * chunk tier fences.
 */
object Plots {

  /** Metres between rows of graves, and between graves in a row. */
  const val ROW_SPACING = 2.6
  const val PLOT_SPACING = 1.8

  /** Metres from a cemetery's wall to its first row. */
  const val INSET = 2.4

  /**
   * The plots of a rectangular cemetery, row by row from the far wall, in a fixed order.
   *
   * Rows run across the facing, so the headstones face along it - towards the gate on the near wall.
   */
  fun cemetery(centre: Vec2d, yaw: Double, halfLength: Double, halfWidth: Double): List<Vec2d> {
    val along = Vec2d(cos(yaw), sin(yaw))
    val across = Vec2d(-along.y, along.x)
    val out = ArrayList<Vec2d>()

    var u = halfLength - INSET
    while (u >= -halfLength + INSET) {
      var v = -halfWidth + INSET
      while (v <= halfWidth - INSET) {
        out.add(centre + along * u + across * v)
        v += PLOT_SPACING
      }
      u -= ROW_SPACING
    }
    return out
  }

  /** The four corners of an oriented rectangle, anticlockwise, as a ring. */
  fun rectangle(centre: Vec2d, yaw: Double, halfLength: Double, halfWidth: Double): Ring {
    val along = Vec2d(cos(yaw), sin(yaw))
    val across = Vec2d(-along.y, along.x)
    return Ring(
      listOf(
        centre + along * halfLength + across * halfWidth,
        centre - along * halfLength + across * halfWidth,
        centre - along * halfLength - across * halfWidth,
        centre + along * halfLength - across * halfWidth
      )
    )
  }
}
