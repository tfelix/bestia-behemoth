package net.bestia.messages;

import net.bestia.entity.component.PositionComponent;

/**
 * This is the central interface for any external component like services or
 * components to interact with the akka system.
 * 
 * @author Thomas Felix
 *
 */
public interface MessageApi {

	/**
	 * The message is send towards the client.
	 * 
	 * @param message
	 *            The message to be send to the client.
	 */
	void sendToClient(JsonMessage message);

	/**
	 * Sends the message to all active player bestias in update range. The given
	 * message must refer to an entity source which has a
	 * {@link PositionComponent} attached. Otherwise the message origin position
	 * can not be determined and thus no updates send to players.
	 * 
	 * @param message
	 *            The message to send to all active players inside the update
	 *            range.
	 */
	void sendToActiveClientsInRange(EntityJsonMessage message);


	/**
	 * Sends a message directly to the entity actor managing a single entity
	 * inside the cluster.
	 * 
	 * @param entityId
	 *            The entity ID which actor should receive the message.
	 * @param msg
	 *            The message to send.
	 */
	void sendToEntity(EntityMessage message);
}
