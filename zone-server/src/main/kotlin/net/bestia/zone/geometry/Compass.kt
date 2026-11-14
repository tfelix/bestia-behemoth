package net.bestia.zone.geometry

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToLong

/** The eight compass points as translation tokens, with x east and y north. */
object Compass {

  /** Counted from east toward north, as `atan2` is. Rows live in the client's `general.csv`. */
  private val TOKENS = arrayOf(
    "DIRECTION_EAST",
    "DIRECTION_NORTHEAST",
    "DIRECTION_NORTH",
    "DIRECTION_NORTHWEST",
    "DIRECTION_WEST",
    "DIRECTION_SOUTHWEST",
    "DIRECTION_SOUTH",
    "DIRECTION_SOUTHEAST",
  )

  val POINTS = TOKENS.size

  /** The nearest of the eight points from a step, 0 east and counting toward north. */
  fun octantOf(dx: Double, dy: Double): Int {
    return Math.floorMod((atan2(dy, dx) / (PI / 4)).roundToLong(), POINTS.toLong()).toInt()
  }

  fun tokenOf(octant: Int): String {
    return TOKENS[Math.floorMod(octant, POINTS)]
  }

  fun tokenOf(dx: Double, dy: Double): String {
    return tokenOf(octantOf(dx, dy))
  }
}
