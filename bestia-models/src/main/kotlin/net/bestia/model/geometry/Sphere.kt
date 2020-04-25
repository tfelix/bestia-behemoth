package net.bestia.model.geometry

import kotlin.math.sqrt

/**
 * Represents a circle collision shape.
 *
 * @author Thomas Felix
 */
data class Sphere(
    val center: Vec3,

    /**
     * Returns the radius of the circle.
     */
    val radius: Int,

    override val anchor: Vec3
) : Shape {

  init {
    require(radius >= 0) { "Radius can not be negative." }

    checkAnchor(anchor.x, anchor.y)
  }

  override val boundingBox: Cube
    get() {
      val dX = center.x - radius
      val dY = center.y - radius
      val dZ = center.z - radius

      return Cube(dX, dY, dZ, (2 * radius).toLong(), (2 * radius).toLong(), (2 * radius).toLong())
    }

  /**
   * Creates a circular collision shape. The anchor of the circle is
   * positioned at the center of the circle.
   *
   * @param x
   * X center coordinate.
   * @param y
   * Y center coordinate.
   * @param radius
   * The radius of the circle.
   */
  constructor(x: Long, y: Long, z: Long, radius: Int) : this(Vec3(x, y, z), radius, Vec3(x, y, z))

  constructor(x: Long, y: Long, z: Long, radius: Int, anchorX: Long, anchorY: Long, anchorZ: Long)
      : this(Vec3(x, y, z), radius, Vec3(anchorX, anchorY, anchorZ))

  private fun checkAnchor(x: Long, y: Long) {
    val dX = center.x - x
    val dY = center.y - y
    require(sqrt((dX * dX + dY * dY).toDouble()) <= radius + 1) { "Anchor must be inside the circle." }
  }

  override fun collide(s: Vec3): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Sphere): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Cube): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Shape): Boolean {
    return s.collide(this)
  }

  override fun moveTo(x: Long, y: Long, z: Long): Sphere {
    val dX = center.x - anchor.x
    val dY = center.y - anchor.y
    val dZ = center.z - anchor.z

    val cX = x + dX
    val cY = y + dY
    val cZ = z + dZ

    return Sphere(cX, cY, cZ, radius, x, y, z)
  }
}
