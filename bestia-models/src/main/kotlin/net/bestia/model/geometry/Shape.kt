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
  val boundingBox: Rect

  /**
   * Returns the anchor coordinates for this shape. These are absolute
   * coordinates in world space.
   *
   * @return The anchor coordiantes in world space.
   */
  val anchor: Point

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Point): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Circle): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Rect): Boolean

  /**
   * Checks if this shape collides with the given vector.
   *
   * @param s
   * Collding shape.
   * @return TRUE if it collides. FALSE otherwise.
   */
  fun collide(s: Shape): Boolean

  /**
   * Moves the whole [Shape] to the new coordinates relative to
   * its anchor point whose absolute coordinates are now set by this method.
   *
   * @param x
   * New absolute x coordinate.
   * @param y
   * New absolute y coordinate.
   * @return A new collision shape which is move
   */
  fun moveByAnchor(x: Long, y: Long): Shape
}
