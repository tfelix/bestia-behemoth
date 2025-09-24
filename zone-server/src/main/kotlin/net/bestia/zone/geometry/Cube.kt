package net.bestia.zone.geometry

import java.io.Serializable

/**
 * Cube. Immutable. Can be used as collision bounding box shape and other
 * things.
 *
 * @author Thomas Felix
 */
data class Cube(
  val origin: Vec3L,
  val size: Size
) : Serializable {

  constructor(
      x: Long,
      y: Long,
      z: Long,
      width: Long,
      height: Long,
      depth: Long
  ) : this(
      Vec3L(x, y, z),
      Size(width, height, depth)
  )

  /**
   * Ctor. Creates a bounding box at x and y equals 0.
   */
  constructor (width: Long, height: Long, depth: Long) : this(0, 0, 0, width, height, depth)

  /**
   * Returns the width.
   */
  val width: Long
    get() = size.width

  /**
   * Returns the height.
   */
  val height: Long
    get() = size.height

  val depth: Long
    get() = size.depth

  /**
   * Returns the x value. Offset of the box from origin.
   */
  val x: Long
    get() = origin.x

  /**
   * Returns the y value. Offset of the box from origin.
   */
  val y: Long
    get() = origin.y

  val z: Long
    get() = origin.z

  /**
   * Checks if the given coordinate lies within the cube.
   */
  fun collide(pos: Vec3L): Boolean {
    return pos.x >= x && pos.x <= x + width &&
           pos.y >= y && pos.y <= y + height &&
           pos.z >= z && pos.z <= z + depth
  }

  /**
   * Checks if two cubes intersect.
   */
  fun intersects(other: Cube): Boolean {
    return x < other.x + other.width && x + width > other.x &&
           y < other.y + other.height && y + height > other.y &&
           z < other.z + other.depth && z + depth > other.z
  }
}
