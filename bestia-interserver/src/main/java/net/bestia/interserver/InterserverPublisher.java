package net.bestia.interserver;

import java.io.IOException;

import net.bestia.messages.Message;

public interface InterserverPublisher {
	
	/**
	 * Connects the publisher to the interserver.
	 */
	public void connect();
	
	/**
	 * Disconnects the publisher from the interserver.
	 */
	public void disconnect();

	/**
	 * Sends a message to the interserver. The publisher will handle the topic
	 * of the message itself. If the Publisher is not connected to a interserver
	 * instance publishing a message will throw an IOException.
	 * 
	 * @param msg
	 *            Message to be send to the Interserver.
	 */
	public void publish(Message msg) throws IOException;
}
