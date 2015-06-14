package net.bestia.model.service;

import net.bestia.messages.Message;

/**
 * Implementer of this interface must be able to send messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface MessageSender {

	/**
	 * Sends a message to the user/client.
	 * 
	 * @param message Message to be send.
	 */
	public void sendMessage(Message message);
}
