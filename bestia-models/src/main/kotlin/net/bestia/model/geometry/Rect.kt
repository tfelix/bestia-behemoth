package net.bestia.model.geometry

import java.io.Serializable

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * Rectangle. Immutable. Can be used as collision bounding box shape and other
 * things.
 *
 * @author Thomas Felix
 */
data class Rect(
    @JsonProperty("o")
    val origin: Point,

    @JsonProperty("s")
    private val size: Size,

    @JsonProperty("a")
    override val anchor: Point
) : Shape, Serializable {

  init {
    checkNotNegative(width, height)
    checkAnchor(anchor.x, anchor.y)
  }

  constructor(
      x: Long,
      y: Long,
      width: Long,
      height: Long
  ) : this(
      Point(x, y),
      Size(width, height),
      Point(x + width / 2, y + height / 2)
  )

  constructor(
      x: Long,
      y: Long,
      width: Long,
      height: Long,
      anchorX: Long,
      anchorY: Long
  ) : this(
      Point(x, y),
      Size(width, height),
      Point(anchorX, anchorY)
  )

  /**
   * Ctor. Createa a bounding box at x and y equals 0. The anchor is set
   * default to the middle.
   *
   * @param width
   * Width
   * @param height
   * Height
   */
  constructor (width: Long, height: Long) : this(0, 0, width, height)

  /**
   * Returns the width.
   *
   * @return Width.
   */
  val width: Long
    get() = size.width

  /**
   * Returns the height.
   *
   * @return Height.
   */
  val height: Long
    get() = size.height

  /**
   * Returns the x value. Offset of the box from origin.
   *
   * @return x
   */
  val x: Long
    get() = origin.x

  /**
   * Returns the y value. Offset of the box from origin.
   *
   * @return y
   */
  val y: Long
    get() = origin.y

  override val boundingBox: Rect
    get() = this

  private fun checkAnchor(aX: Long, aY: Long) {
    val isXInside = origin.x <= aX && aX <= origin.x + size.width

    if (!isXInside) {
      throw IllegalArgumentException("Anchor X must be inside the rectangle.")
    }

    val isYInside = origin.y <= aY && aY <= origin.y + size.height

    if (!isYInside) {
      throw IllegalArgumentException("Anchor Y must be inside the rectangle.")
    }
  }

  private fun checkNotNegative(width: Long, height: Long) {
    if (width < 0) {
      throw IllegalArgumentException("Width can not be null.")
    }
    if (height < 0) {
      throw IllegalArgumentException("Height can not be null.")
    }
  }

  /**
   * Checks if the given coordinate lies within the rectangle.
   *
   * @param s
   * Coordinates to check against this rectangle.
   * @return TRUE if it lies within, FALSE otherwise.
   */
  override fun collide(s: Point): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Circle): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Rect): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Shape): Boolean {
    return s.collide(this)
  }

  override fun moveByAnchor(x: Long, y: Long): Rect {
    val cX = x + x - anchor.x
    val cY = y + y - anchor.y

    return Rect(Point(cX, cY), Size(width, height), Point(x, y))
  }
}
