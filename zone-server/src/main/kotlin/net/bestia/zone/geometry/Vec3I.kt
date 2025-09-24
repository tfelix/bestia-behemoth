package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

@Embeddable
 data class Vec3I(
  override val x: Int,
  override val y: Int,
  override val z: Int
) : Vec3<Int>, Serializable {
  constructor(x: Long, y: Long, z: Long) : this(x.toInt(), y.toInt(), z.toInt())
  constructor() : this(0, 0, 0)

  fun distance(p: Vec3I): Double {
    val dx = x - p.x
    val dy = y - p.y
    val dz = z - p.z
    return sqrt((dx * dx + dy * dy + dz * dz).toDouble())
  }

  operator fun plus(rhs: Vec3I) = Vec3I(x + rhs.x, y + rhs.y, z + rhs.z)
  operator fun minus(rhs: Vec3I) = Vec3I(x - rhs.x, y - rhs.y, z - rhs.z)
  operator fun times(scalar: Int) = Vec3I(x * scalar, y * scalar, z * scalar)

  companion object {
    val ZERO = Vec3I(0, 0, 0)
  }
}