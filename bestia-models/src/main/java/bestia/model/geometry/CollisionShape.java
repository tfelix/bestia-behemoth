package bestia.model.geometry;

/**
 * The interface provides a common interface for the existing collision shapes.
 * These are used by the game to determine if a collision has happened.
 * 
 * @author Thomas Felix
 *
 */
//@JsonTypeInfo(include=As.WRAPPER_OBJECT, use=Id.NAME)
public interface CollisionShape {

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	boolean collide(Point s);

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	boolean collide(Circle s);

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	boolean collide(Rect s);

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	boolean collide(CollisionShape s);

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	public Rect getBoundingBox();

	/**
	 * Returns the anchor coordinates for this shape. These are absolute
	 * coordinates in world space.
	 * 
	 * @return The anchor coordiantes in world space.
	 */
	public Point getAnchor();

	/**
	 * Moves the whole {@link CollisionShape} to the new coordinates relative to
	 * its anchor point whose absolute coordinates are now set by this method.
	 * 
	 * @param x
	 *            New absolute x coordinate.
	 * @param y
	 *            New absolute y coordinate.
	 * @return A new collision shape which is move
	 */
	public CollisionShape moveByAnchor(long x, long y);
}
