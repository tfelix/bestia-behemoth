package net.bestia.messages;

/**
 * Messages are refering to update entities. They must carry an entity id.
 * 
 * @author Thomas Felix
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
