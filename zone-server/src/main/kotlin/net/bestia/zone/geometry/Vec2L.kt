package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

@Embeddable
 data class Vec2L(
  override val x: Long,
  override val y: Long
) : Vec2<Long>, Serializable {
  constructor(x: Int, y: Int) : this(x.toLong(), y.toLong())
  constructor() : this(0, 0)

  fun distance(p: Vec2L): Double {
    val dx = x - p.x
    val dy = y - p.y
    return sqrt((dx * dx + dy * dy).toDouble())
  }

  operator fun plus(rhs: Vec2L) = Vec2L(x + rhs.x, y + rhs.y)
  operator fun minus(rhs: Vec2L) = Vec2L(x - rhs.x, y - rhs.y)
  operator fun times(scalar: Long) = Vec2L(x * scalar, y * scalar)

  companion object {
    val ZERO = Vec2L(0, 0)
  }
}