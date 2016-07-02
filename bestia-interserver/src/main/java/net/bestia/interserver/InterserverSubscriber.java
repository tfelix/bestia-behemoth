package net.bestia.interserver;

import java.io.IOException;

/**
 * Used by classes to connect to the interserver in a subscribing way. Incoming
 * messages will be received via topics to which one can subscribe. Depending on
 * the arriving messages these topics will be triggered.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public interface InterserverSubscriber {

	/**
	 * Connects to the interserver.
	 * 
	 * @return {@code TRUE} if connection is successful. {@code FALSE}
	 *         otherwise.
	 * @throws IOException
	 *             If the connection was not successful.
	 */
	public void connect() throws IOException;

	/**
	 * Disconnects from the interserver. WARNING: This should only be called
	 * from a factory which is shutting down.
	 */
	public void disconnect();

	/**
	 * This will subscribe the message system to a topic from which we will
	 * receive notations and data.
	 * 
	 * @param topic
	 *            The topic to subscribe to.
	 */
	public void subscribe(String topic);

	/**
	 * Unsubscribe from the given topic.
	 * 
	 * @param topic
	 *            The topic to unsubscribe from.
	 */
	public void unsubscribe(String topic);
}