package net.bestia.model.geometry

/**
 * Represents a circle collision shape.
 *
 * @author Thomas Felix
 */
data class Circle(
    val center: Point,

    /**
     * Returns the radius of the circle.
     */
    val radius: Int,

    override val anchor: Point
) : Shape {

  init {
    if (radius < 0) {
      throw IllegalArgumentException("Radius can not be negative.")
    }

    checkAnchor(anchor.x, anchor.y)
  }

  override val boundingBox: Rect
    get() {
      val leftX = center.x - radius
      val topY = center.y - radius

      return Rect(leftX, topY, (2 * radius).toLong(), (2 * radius).toLong())
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
  constructor(x: Long, y: Long, radius: Int) : this(Point(x, y), radius, Point(x, y))

  constructor(x: Long, y: Long, radius: Int, anchorX: Long, anchorY: Long)
      : this(Point(x, y), radius, Point(anchorX, anchorY))

  private fun checkAnchor(x: Long, y: Long) {
    val dX = center.x - x
    val dY = center.y - y
    if (Math.sqrt((dX * dX + dY * dY).toDouble()) > radius + 1) {
      throw IllegalArgumentException("Anchor must be inside the circle.")
    }
  }

  override fun collide(s: Point): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Circle): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Rect): Boolean {
    return CollisionHelper.collide(this, s)
  }

  override fun collide(s: Shape): Boolean {
    return s.collide(this)
  }

  override fun moveTo(x: Long, y: Long): Circle {
    val dX = center.x - anchor.x
    val dY = center.y - anchor.y

    val cX = x + dX
    val cY = y + dY

    return Circle(cX, cY, radius, x, y)
  }
}
