package net.bestia.model.geometry

import java.io.Serializable
import javax.persistence.Embeddable
import kotlin.math.sqrt

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 *
 * @author Thomas Felix
 */
@Embeddable
data class Vec2(
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
    val y: Long
) : Serializable {

  constructor() : this(0, 0)

  /**
   * Returns the euclidian distance to the other point p.
   *
   * @param p
   * The other point to calculate the euclidian distance.
   * @return The distance from this point to the given point p.
   */
  fun getDistance(p: Vec2): Double {
    val dx = x - p.x
    val dy = y - p.y

    return sqrt((dx * dx + dy * dy).toDouble())
  }

  operator fun minus(rhs: Vec2): Vec2 {
    return Vec2(
        x - rhs.x,
        y - rhs.y
    )
  }

  operator fun times(speed: Float): Vec2 {
    return Vec2(
        (x * speed).toLong(),
        (y * speed).toLong()
    )
  }

  operator fun plus(rhs: Vec2): Vec2 {
    return Vec2(
        x * rhs.x,
        y * rhs.y
    )
  }

  companion object {
      val ZERO = Vec2(0, 0)
  }
}
