package net.bestia.zoneserver.actor;

import net.bestia.messages.JsonMessage;

/**
 * This is the interface for a typed actor for internal message routing.
 * 
 * @author Thomas Felix
 *
 */
public interface ZoneAkkaApi {

	/**
	 * The message is send towards the client.
	 * 
	 * @param message
	 *            The message to be send to the client.
	 */
	void sendToClient(JsonMessage message);

}
