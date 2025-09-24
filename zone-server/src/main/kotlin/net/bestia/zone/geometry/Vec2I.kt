package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

@Embeddable
data class Vec2I(
  override val x: Int,
  override val y: Int
) : Vec2<Int>, Serializable {
  constructor(x: Long, y: Long) : this(x.toInt(), y.toInt())
  constructor() : this(0, 0)

  fun distance(p: Vec2I): Double {
    val dx = x - p.x
    val dy = y - p.y
    return sqrt((dx * dx + dy * dy).toDouble())
  }

  operator fun plus(rhs: Vec2I) = Vec2I(x + rhs.x, y + rhs.y)
  operator fun minus(rhs: Vec2I) = Vec2I(x - rhs.x, y - rhs.y)
  operator fun times(scalar: Int) = Vec2I(x * scalar, y * scalar)

  companion object {
    val ZERO = Vec2I(0, 0)
  }
}