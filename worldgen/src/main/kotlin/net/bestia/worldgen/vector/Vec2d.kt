package net.bestia.worldgen.vector

import kotlin.math.sqrt

/**
 * A position or direction in world space, in metres, at full precision.
 *
 * Vector features are stored in world space and never quantized to a grid - this is the type
 * that carries that promise. It is deliberately separate from `net.bestia.zone.geometry.Vec2F`,
 * which is a JPA-annotated float type belonging to the runtime entity layer.
 */
data class Vec2d(
  val x: Double,
  val y: Double
) {

  operator fun plus(rhs: Vec2d) = Vec2d(x + rhs.x, y + rhs.y)
  operator fun minus(rhs: Vec2d) = Vec2d(x - rhs.x, y - rhs.y)
  operator fun times(scalar: Double) = Vec2d(x * scalar, y * scalar)
  operator fun unaryMinus() = Vec2d(-x, -y)

  infix fun dot(rhs: Vec2d) = x * rhs.x + y * rhs.y

  /** 2D cross product, i.e. the z component of the 3D cross. Positive when [rhs] is left of this. */
  infix fun cross(rhs: Vec2d) = x * rhs.y - y * rhs.x

  val length get() = sqrt(x * x + y * y)
  val lengthSquared get() = x * x + y * y

  fun normalized(): Vec2d {
    val l = length
    return if (l == 0.0) ZERO else Vec2d(x / l, y / l)
  }

  /** Rotated 90 degrees counter-clockwise. The left-hand normal of a tangent. */
  fun perpendicular() = Vec2d(-y, x)

  fun distanceTo(p: Vec2d): Double {
    val dx = x - p.x
    val dy = y - p.y
    return sqrt(dx * dx + dy * dy)
  }

  fun distanceSquaredTo(p: Vec2d): Double {
    val dx = x - p.x
    val dy = y - p.y
    return dx * dx + dy * dy
  }

  fun lerp(to: Vec2d, t: Double) = Vec2d(x + (to.x - x) * t, y + (to.y - y) * t)

  companion object {
    val ZERO = Vec2d(0.0, 0.0)
  }
}
