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
    val origin: Vec3,
    private val size: Size,
    override val anchor: Vec3
) : Shape, Serializable {

  init {
    checkNotNegative(width, height)
    checkAnchor(anchor.x, anchor.y)
  }

  constructor(
      x: Long,
      y: Long,
      z: Long,
      width: Long,
      height: Long,
      depth: Long
  ) : this(
      Vec3(x, y, z),
      Size(width, height, depth),
      Vec3(x + width / 2, y + height / 2, z + depth / 2)
  )

  constructor(
      x: Long,
      y: Long,
      z: Long,
      width: Long,
      height: Long,
      depth: Long,
      anchorX: Long,
      anchorY: Long,
      anchorZ: Long
  ) : this(
      Vec3(x, y, z),
      Size(width, height, depth),
      Vec3(anchorX, anchorY, anchorZ)
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
  constructor (width: Long, height: Long, depth: Long) : this(0, 0, 0, width, height, depth)

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

  val depth: Long
    get() = size.depth

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

  val z: Long
    get() = origin.z

  override val boundingBox: Rect
    get() = this

  private fun checkAnchor(aX: Long, aY: Long) {
    val isXInside = origin.x <= aX && aX <= origin.x + size.width

    require(isXInside) { "Anchor X must be inside the rectangle." }

    val isYInside = origin.y <= aY && aY <= origin.y + size.height

    require(isYInside) { "Anchor Y must be inside the rectangle." }
  }

  private fun checkNotNegative(width: Long, height: Long) {
    require(width >= 0) { "Width can not be null." }
    require(height >= 0) { "Height can not be null." }
  }

  /**
   * Checks if the given coordinate lies within the rectangle.
   *
   * @param s
   * Coordinates to check against this rectangle.
   * @return TRUE if it lies within, FALSE otherwise.
   */
  override fun collide(s: Vec3): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Sphere): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Rect): Boolean {
    return CollisionHelper.collide(s, this)
  }

  override fun collide(s: Shape): Boolean {
    return s.collide(this)
  }

  override fun moveTo(x: Long, y: Long, z: Long): Rect {
    val dX = anchor.x - origin.x
    val dY = anchor.y - origin.y
    val dZ = anchor.z - origin.z
    val cX = x - dX
    val cY = y - dY
    val cZ = z - dZ

    return Rect(Vec3(cX, cY, cZ), Size(width, height, depth), Vec3(x, y, z))
  }
}
