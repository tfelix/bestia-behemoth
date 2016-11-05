package net.bestia.zoneserver.entity.traits;

import net.bestia.model.shape.Point;

/**
 * Entity has a defined position in the world. Point refers to the anchor point
 * of a sprite if there is any.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Locatable extends IdEntity {

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

}
