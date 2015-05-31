package net.bestia.interserver;

import net.bestia.messages.Message;

/**
 * This interface describes a listener which is used to fire a callback upon
 * incoming messages.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface InterserverMessageHandler {
	/**
	 * Is called if a message is received from the interserver.
	 * 
	 * @param msg
	 *            Message which was received by the interserver.
	 */
	public void onMessage(Message msg);

	/**
	 * Handler gets called if a connection to be interserver is lost and can not
	 * be reestablished.
	 */
	public void connectionLost();
}
