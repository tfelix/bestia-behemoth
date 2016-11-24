package net.bestia.zoneserver.entity.traits;

import net.bestia.model.shape.Collision;

/**
 * This trait marks the entity as able to collide with another one.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface Collidable {

	Collision getCollision();

	/**
	 * Performs a collision with another collidable entity.
	 * 
	 * @param collider
	 */
	void collide(Collidable collider);

}
