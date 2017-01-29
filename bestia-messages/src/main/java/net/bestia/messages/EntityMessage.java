package net.bestia.messages;

/**
 * Messages are used to update entities. They must carry an entity id.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface EntityMessage {

	/**
	 * Returns the entity id to which this message belongs.
	 * 
	 * @return The entity id.
	 */
	long getEntityId();

}
