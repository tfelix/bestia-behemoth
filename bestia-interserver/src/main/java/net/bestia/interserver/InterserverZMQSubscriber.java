package net.bestia.interserver;

import java.io.IOException;

import net.bestia.messages.Message;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * With the InterserverSubscriber it is possible to connect to the interserver and listen to certain topics. If a
 * message for this topic will be published a callback via an InterserverListener will be issued.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
class InterserverZMQSubscriber implements InterserverSubscriber {

	/**
	 * This thread processes incoming messages from the zone or the webserver and puts them into the message queue. The
	 * messages will be published again under a certain path depending on the kind of message so subscriber can react to
	 * the messages.
	 *
	 */
	private class MessageConsumerThread extends Thread {

		private final Socket subscriber;

		public MessageConsumerThread(Socket subscriber) {
			this.setName("MessageConsumerThread");

			this.subscriber = subscriber;
		}

		@Override
		public void run() {
			
			subscriber.connect(url);
			log.debug("Connected to interserver on {}.", url);
			
			subscribeDefaultTopics();
			
			while (!Thread.currentThread().isInterrupted()) {
				try {
					// Receive the topic name. Throw it away we only need data.
					subscriber.recvStr();
					byte[] data = subscriber.recv();
					log.trace("Received message of {} byte.", data.length);
					Message msg = (Message) ObjectSerializer.deserializeObject(data);
					listener.onMessage(msg);
				} catch (ClassNotFoundException | IOException ex) {
					log.error("Could not create instance of message.", ex);
					break;
				} catch(ZMQException ex) {
					if (ex.getErrorCode () == ZMQ.Error.ETERM.getCode ()) {
                        break;
                    }
				}
			}
			
			subscriber.close();
			
			log.trace("MessageConsumerThread has ended.");
		}

		private void subscribeDefaultTopics() {
			// Default server channel.
			subscribe("servers");
		}
	}

	private final Logger log = LogManager.getLogger(InterserverZMQSubscriber.class);

	private final Socket subscriber;
	private final String url;
	private final InterserverMessageHandler listener;
	private final MessageConsumerThread thread;

	/**
	 * Creates a subscriber for a ZMQ socket.
	 * 
	 * @param listener
	 *            Handler to be called when an messages is incoming.
	 * @param url
	 *            URL to connect to.
	 * @param ctx
	 *            ZMQ context to create the socket.
	 */
	public InterserverZMQSubscriber(InterserverMessageHandler listener, String url, Context ctx) {
		subscriber = ctx.socket(ZMQ.SUB);

		this.url = url;
		this.listener = listener;
		this.thread = new MessageConsumerThread(subscriber);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverSubscriber#connect()
	 */
	@Override
	public void connect() {
		log.debug("Connecting to interserver...");	
		thread.start();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverSubscriber#disconnect()
	 */
	@Override
	public void disconnect() {
		try {
			thread.interrupt();
			thread.join();
		} catch (InterruptedException e) {
			log.warn("Could not properly close the socket.", e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverSubscriber#subscribe(java.lang.String)
	 */
	@Override
	public void subscribe(String topic) {
		log.trace("Subscribing to topic: {}", topic);
		subscriber.subscribe(topic.getBytes());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see net.bestia.interserver.InterserverSubscriber#unsubscribe(java.lang.String)
	 */
	@Override
	public void unsubscribe(String topic) {
		log.trace("Unsubscribing from topic: {}", topic);
		subscriber.unsubscribe(topic.getBytes());
	}
}
