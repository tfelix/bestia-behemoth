package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

@Embeddable
 data class Vec2F(
  override val x: Float,
  override val y: Float
) : Vec2<Float>, Serializable {
  constructor(x: Int, y: Int) : this(x.toFloat(), y.toFloat())
  constructor() : this(0f, 0f)

  fun distance(p: Vec2F): Float {
    val dx = x - p.x
    val dy = y - p.y
    return sqrt(dx * dx + dy * dy)
  }

  operator fun plus(rhs: Vec2F) = Vec2F(x + rhs.x, y + rhs.y)
  operator fun minus(rhs: Vec2F) = Vec2F(x - rhs.x, y - rhs.y)
  operator fun times(scalar: Float) = Vec2F(x * scalar, y * scalar)

  companion object {
    val ZERO = Vec2F(0f, 0f)
  }
}