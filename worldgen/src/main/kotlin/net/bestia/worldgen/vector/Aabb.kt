package net.bestia.worldgen.vector

import kotlin.math.max
import kotlin.math.min

/**
 * Axis aligned bounding box in world space (metres), max-inclusive.
 *
 * Feature bounding boxes are always stored already expanded by the feature's influence radius,
 * so a spatial index hit means "could possibly influence", and a miss means "definitely cannot".
 */
data class Aabb(
  val minX: Double,
  val minY: Double,
  val maxX: Double,
  val maxY: Double
) {

  init {
    require(minX <= maxX && minY <= maxY) { "Degenerate Aabb: ($minX,$minY)-($maxX,$maxY)" }
  }

  val width get() = maxX - minX
  val height get() = maxY - minY
  val centerX get() = (minX + maxX) * 0.5
  val centerY get() = (minY + maxY) * 0.5

  fun expanded(margin: Double) = Aabb(minX - margin, minY - margin, maxX + margin, maxY + margin)

  fun contains(x: Double, y: Double) = x in minX..maxX && y in minY..maxY

  fun intersects(other: Aabb) =
    minX <= other.maxX && maxX >= other.minX && minY <= other.maxY && maxY >= other.minY

  fun union(other: Aabb) = Aabb(
    min(minX, other.minX),
    min(minY, other.minY),
    max(maxX, other.maxX),
    max(maxY, other.maxY)
  )

  companion object {
    fun around(points: Iterable<Vec2d>): Aabb {
      var minX = Double.POSITIVE_INFINITY
      var minY = Double.POSITIVE_INFINITY
      var maxX = Double.NEGATIVE_INFINITY
      var maxY = Double.NEGATIVE_INFINITY

      for (p in points) {
        minX = min(minX, p.x)
        minY = min(minY, p.y)
        maxX = max(maxX, p.x)
        maxY = max(maxY, p.y)
      }

      require(minX <= maxX) { "Cannot build an Aabb around an empty point set" }

      return Aabb(minX, minY, maxX, maxY)
    }
  }
}
