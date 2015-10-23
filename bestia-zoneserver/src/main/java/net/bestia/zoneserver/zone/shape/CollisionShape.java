package net.bestia.zoneserver.zone.shape;

/**
 * The interface provides a common interface for the existing collision shapes.
 * These are used by the game to determine if a collision has happened.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface CollisionShape {

	/**
	 * Checks if this shape collides with the given vector.
	 * 
	 * @param s
	 *            Collding shape.
	 * @return TRUE if it collides. FALSE otherwise.
	 */
	boolean collide(Vector2 s);

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
	public Vector2 getAnchor();
}
