package net.bestia.model.geometry

import java.io.Serializable

import javax.persistence.Embeddable

import com.fasterxml.jackson.annotation.JsonIgnore
import kotlin.math.sqrt

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 *
 * @author Thomas Felix
 */
@Embeddable
data class Vec3(
    /**
     * The X coordinate of this point.
     *
     * @return X
     */
    val x: Long,

    /**
     * The Y coordinate of this point.
     *
     * @return Y
     */
    val y: Long,

    val z: Long
) : Shape, Serializable {

  constructor() : this(0, 0, 0)

  override val boundingBox: Cube
    @JsonIgnore
    get() = Cube(x, y, z, 1, 1, 1)

  override val anchor: Vec3
    @JsonIgnore
    get() = this

  override fun collide(s: Vec3): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Sphere): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Cube): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Shape): Boolean {
    return s.collide(this)
  }

  override fun moveTo(x: Long, y: Long, z: Long): Vec3 {
    return Vec3(x, y, z)
  }

  /**
   * Returns the euclidian distance to the other point p.
   *
   * @param p
   * The other point to calculate the euclidian distance.
   * @return The distance from this point to the given point p.
   */
  fun getDistance(p: Vec3): Double {
    val dx = x - p.x
    val dy = y - p.y

    return sqrt((dx * dx + dy * dy).toDouble())
  }

  operator fun minus(rhs: Vec3): Vec3 {
    return Vec3(
        x - rhs.x,
        y - rhs.y,
        z - rhs.z
    )
  }

  operator fun times(speed: Float): Vec3 {
    return Vec3(
        (x * speed).toLong(),
        (y * speed).toLong(),
        (z * speed).toLong()
    )
  }

  operator fun plus(rhs: Vec3): Vec3 {
    return Vec3(
        x * rhs.x,
        y * rhs.y,
        z * rhs.z
    )
  }

  companion object {
      val ZERO = Vec3(0, 0, 0)
  }
}
