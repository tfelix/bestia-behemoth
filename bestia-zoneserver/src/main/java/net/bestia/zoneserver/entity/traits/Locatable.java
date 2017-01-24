package net.bestia.zoneserver.entity.traits;

import java.util.List;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.model.geometry.Point;

/**
 * Entity has a defined position in the world. Point refers to the anchor point
 * of a sprite if there is any.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Locatable extends Entity {

	/**
	 * The position of the entity in the world.
	 * 
	 * @return
	 */
	Point getPosition();

	/**
	 * Sets the position of the entity inside the world.
	 * 
	 * @param x
	 * @param y
	 */
	void setPosition(long x, long y);

	/**
	 * Returns the current movement speed. 1 is the nominal speed which is 1.4
	 * m/s (or 1.4 tiles per second). The minimum speed returned via this method
	 * is 0.01 otherwise it is clamped to 0 (which means the unit can no longer
	 * move). The upper limit is 10.
	 * 
	 * @return The current movement speed of the unit.
	 */
	float getMovementSpeed();

	/**
	 * Returns the space unit which is used by this entity and can be used to
	 * perform collision checks.
	 * 
	 * @return The {@link CollisionShape} of this unit.
	 */
	CollisionShape getShape();

	/**
	 * Sets a new {@link CollisionShape} for this entity unit.
	 * 
	 * @param shape
	 *            The new collision shape. Can not be null.
	 */
	void setShape(CollisionShape shape);

	/**
	 * Moves the entity along a certain path. This is important to be called
	 * from a script context. The can not be any holes inside this path. All
	 * path following elements must have only a distance of 1 from each other.
	 * 
	 * @param path
	 *            The path to follow.
	 */
	void moveTo(List<Point> path);
}
