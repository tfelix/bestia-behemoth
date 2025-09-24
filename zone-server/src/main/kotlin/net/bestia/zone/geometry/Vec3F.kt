package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

@Embeddable
 data class Vec3F(
  override val x: Float,
  override val y: Float,
  override val z: Float
) : Vec3<Float>, Serializable {
  constructor(x: Int, y: Int, z: Int) : this(x.toFloat(), y.toFloat(), z.toFloat())
  constructor() : this(0f, 0f, 0f)

  fun distance(p: Vec3F): Float {
    val dx = x - p.x
    val dy = y - p.y
    val dz = z - p.z
    return sqrt(dx * dx + dy * dy + dz * dz)
  }

  operator fun plus(rhs: Vec3F) = Vec3F(x + rhs.x, y + rhs.y, z + rhs.z)
  operator fun minus(rhs: Vec3F) = Vec3F(x - rhs.x, y - rhs.y, z - rhs.z)
  operator fun times(scalar: Float) = Vec3F(x * scalar, y * scalar, z * scalar)

  companion object {
    val ZERO = Vec3F(0f, 0f, 0f)
  }
}