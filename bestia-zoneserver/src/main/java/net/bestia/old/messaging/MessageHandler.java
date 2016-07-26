package net.bestia.zoneserver.messaging;

import net.bestia.messages.Message;

/**
 * The MessageConsumer works with bestia {@link Message}s. It will take messages
 * and do something with them. Usually the message handler should have their own
 * message queue in order to not cloque up the messaging system. There might be
 * exceptions to this rule.
 * 
 * @author Thomas Felix <thomas.felix.de>
 *
 */
public interface MessageHandler {

	/**
	 * Process the incoming message. This means either process them directly by
	 * creating a command or re-send it or do otherwise some operations with it,
	 * depending on the concrete processor.
	 * 
	 * @param msg
	 *            The message to be processed.
	 */
	public void handleMessage(Message msg);

}
