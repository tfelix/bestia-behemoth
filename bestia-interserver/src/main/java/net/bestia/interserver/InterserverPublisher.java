package net.bestia.interserver;

import java.io.IOException;

import net.bestia.messages.Message;

/**
 * Used by classes able to connect to the interserver. They can be used to send messages to this server.
 * 
 * @author Thomas
 *
 */
public interface InterserverPublisher {

	/**
	 * Connects the publisher to the interserver.
	 * 
	 * @throws IOException
	 *             If the connection was not successful.
	 */
	public void connect() throws IOException;

	/**
	 * Disconnects the publisher from the interserver.
	 */
	public void disconnect();

	/**
	 * Sends a message to the interserver. The publisher will handle the topic of the message itself. If the Publisher
	 * is not connected to a interserver instance publishing a message will throw an IOException.
	 * 
	 * @param msg
	 *            Message to be send to the Interserver.
	 */
	public void publish(Message msg) throws IOException;
}
