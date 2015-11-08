package net.bestia.zoneserver.messaging.preprocess;

import net.bestia.messages.Message;

/**
 * The MessageConsumer works with bestia {@link Message}s. It will take messages
 * and do something with them.
 * 
 * @author Thomas Felix <thomas.felix.de>
 *
 */
public interface MessageProcessor {

	/**
	 * Process the incoming message. This means either process them directly by
	 * creating a command or re-send it or do otherwise some operations with it,
	 * depending on the concrete processor.
	 * 
	 * @param msg
	 *            The message to be processed.
	 */
	public void processMessage(Message msg);

}
