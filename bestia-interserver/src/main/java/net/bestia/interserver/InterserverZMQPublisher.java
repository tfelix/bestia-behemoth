/**
 * 
 */
package net.bestia.interserver;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import net.bestia.messages.Message;

/**
 * Implements the publisher interface to connect to the interserver and publish messages to it via the ZMQ library.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class InterserverZMQPublisher implements InterserverPublisher {

	private final static Logger log = LogManager.getLogger(InterserverZMQPublisher.class);

	private final Socket publisher;
	private final String url;
	private final AtomicBoolean isConnected = new AtomicBoolean(false);

	/**
	 * Ctor.
	 * 
	 * @param url
	 *            The url to connect to. In the form of: tcp://DOMAIN:PORT
	 * @param ctx
	 *            ZMQ.Context
	 */
	public InterserverZMQPublisher(String url, Context ctx) {
		this.publisher = ctx.socket(ZMQ.PUSH);
		this.url = url;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverPublisher#connect()
	 */
	@Override
	public void connect() {
		if (isConnected.getAndSet(true) == true) {
			log.warn("Already connected.");
			return;
		}
		log.debug("Connecting to interserver...");

		publisher.connect(url);

		log.debug("Connected to interserver on {}.", url);
	}

	@Override
	public void disconnect() {
		// Close the queue so no more messages are accepted.
		isConnected.set(false);
		publisher.close();

		log.debug("Disconnected from interserver.");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverPublisher#publish(net.bestia.messages.Message)
	 */
	@Override
	public synchronized void publish(Message msg) throws IOException {
		if (!isConnected.get()) {
			throw new IOException("Socket was already closed.");
		}

		final String topic = msg.getMessagePath();
		final byte[] data = ObjectSerializer.serializeObject(msg);
		publisher.sendMore(topic);
		publisher.send(data);
	}

}
