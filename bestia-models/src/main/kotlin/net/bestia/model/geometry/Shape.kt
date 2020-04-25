package net.bestia.model.geometry

/**
 * The interface provides a common interface for the existing collision shapes.
 * These are used by the game to determine if a collision has happened.
 *
 * @author Thomas Felix
 */
interface Shape {

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  val boundingBox: Cube

  /**
   * Returns the anchor coordinates for this shape. These are absolute
   * coordinates in world space.
   *
   * @return The anchor coordiantes in world space.
   */
  val anchor: Vec3

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Vec3): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Sphere): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Cube): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Shape): Boolean

  /**
   * Moves the whole [Shape] to the new coordinates relative to its anchor point
   * whose absolute coordinates are now set by this method.
   *
   * @return A new collision shape which is move
   */
  fun moveTo(x: Long, y: Long, z: Long): Shape

  fun moveTo(p: Vec3): Shape {
    return moveTo(p.x, p.y, p.z)
  }
}
