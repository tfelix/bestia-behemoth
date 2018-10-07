package net.bestia.model.geometry

import java.io.Serializable

import javax.persistence.Embeddable

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 2D Point. Immutable. Used as coordinates in various systems.
 *
 * @author Thomas Felix
 */
@Embeddable
data class Point(
    /**
     * The X coordinate of this point.
     *
     * @return X
     */
    @JsonProperty("x")
    val x: Long,

    /**
     * The Y coordinate of this point.
     *
     * @return Y
     */
    @JsonProperty("y")
    val y: Long
) : CollisionShape, Serializable {

  constructor() : this(0, 0)

  override val boundingBox: Rect
    @JsonIgnore
    get() = Rect(x, y, 1, 1)

  override val anchor: Point
    @JsonIgnore
    get() = this

  override fun collide(s: Point): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Circle): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Rect): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: CollisionShape): Boolean {
    return s.collide(this)
  }

  override fun moveByAnchor(x: Long, y: Long): Point {
    return Point(x, y)
  }

  /**
   * Returns the euclidian distance to the other point p.
   *
   * @param p
   * The other point to calculate the euclidian distance.
   * @return The distance from this point to the given point p.
   */
  fun getDistance(p: Point): Double {
    val dx = x - p.x
    val dy = y - p.y

    return Math.sqrt((dx * dx + dy * dy).toDouble())
  }

  fun minus(rhs: Point): Point {
    return Point(
        x - rhs.x,
        y - rhs.y
    )
  }
}
