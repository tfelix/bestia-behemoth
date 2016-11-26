package net.bestia.zoneserver.entity;

/**
 * This class can takes entities and provides several methods in order to build
 * update messages which are send to the client. It is also responsible for
 * sending it to all players in sight range.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityUpdater {
	
	//public EntityUpdater

	public void updatePosition(BaseEntity entity) {

	}

	/**
	 * Sends an position update of the current entity to the client. This
	 * retrieves the current planned path, as well as speed and current
	 * position.
	 * 
	 * @param entity
	 *            The entity to use the position.
	 */
	public void updatePosition(LivingEntity entity) {

	}
}
