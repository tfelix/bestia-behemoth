package net.bestia.model.service;

import net.bestia.messages.Message;

/**
 * Implementer of this interface must be able to send messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface MessageSender {

	public void sendMessage(Message message);
}
