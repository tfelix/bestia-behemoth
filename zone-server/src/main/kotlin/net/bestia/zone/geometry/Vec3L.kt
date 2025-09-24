package net.bestia.zone.geometry

import jakarta.persistence.Embeddable
import java.io.Serializable
import kotlin.math.sqrt

/**
 * 3D Point. Immutable. Used as coordinates in various systems.
 *
 * @author Thomas Felix
 */
@Embeddable
data class Vec3L (
  /**
   * The X coordinate of this point.
   *
   * @return X
   */
  override val x: Long,

  /**
   * The Y coordinate of this point.
   *
   * @return Y
   */
  override val y: Long,

  override val z: Long
) : Vec3<Long>, Serializable {

  constructor(x: Int, y: Int, z: Int) : this(x.toLong(), y.toLong(), z.toLong())
  constructor() : this(0, 0, 0)

  /**
   * Returns the euclidian distance to the other point p.
   *
   * @param p
   * The other point to calculate the euclidean distance.
   * @return The distance from this point to the given point p.
   */
  fun distance(p: Vec3L): Long {
    val dx = x - p.x
    val dy = y - p.y

    return sqrt((dx * dx + dy * dy).toDouble()).toLong()
  }

  operator fun minus(rhs: Vec3L): Vec3L {
    return Vec3L(
      x - rhs.x,
      y - rhs.y,
      z - rhs.z
    )
  }

  operator fun times(speed: Float): Vec3L {
    return Vec3L(
      (x * speed).toLong(),
      (y * speed).toLong(),
      (z * speed).toLong()
    )
  }

  operator fun plus(rhs: Vec3L): Vec3L {
    return Vec3L(
      x + rhs.x,
      y + rhs.y,
      z + rhs.z
    )
  }

  companion object {
    val ZERO = Vec3L(0, 0, 0)
  }
}