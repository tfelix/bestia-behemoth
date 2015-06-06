package net.bestia.interserver;

import java.io.IOException;
import java.nio.channels.ClosedSelectorException;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;

/**
 * The Interserver builds the bestia system backbone. It will receive messages from the webserver and will relay this
 * information to the zone server. These are able to subscribe to account topics which will then receive all
 * communication from this account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);

	/**
	 * This thread processes incoming messages from the zone or the webserver and puts them into the message queue. The
	 * messages will be published again under a certain path depending on the kind of message so subscriber can react to
	 * the messages.
	 *
	 */
	private class MessageSubscriberThread extends Thread {

		private final Socket subscriber;
		public final AtomicBoolean isRunning = new AtomicBoolean(true);

		public MessageSubscriberThread(Socket subscriber) {
			this.setName("MessageSubscriberThread");
			this.subscriber = subscriber;
		}

		@Override
		public void run() {
			try {
				while (isRunning.get()) {

					final String topic = subscriber.recvStr();
					final byte[] data = subscriber.recv();
					log.trace("Received message[topic: {}, size {} byte]", topic, data.length);

					publisher.sendMore(topic);
					publisher.send(data);
				}
			} catch (ClosedSelectorException ex) {
				log.trace("Socket was externally closed.");
			}
		}
	}

	private MessageSubscriberThread subscriberThread;

	private Context context;
	private Socket publisher;
	private Socket subscriber;

	private final String publishUrl;
	private final String subscriberUrl;

	/**
	 * 
	 * @param config
	 *            Loaded BestiaConfiguration.
	 */
	public Interserver(BestiaConfiguration config) {
		if (!config.isLoaded()) {
			throw new IllegalArgumentException("BestiaConfiguration is not loaded.");
		}

		context = ZMQ.context(config.getIntProperty("inter.threads"));
		publishUrl = "tcp://" + config.getProperty("inter.domain") + ":" + config.getProperty("inter.publishPort");
		subscriberUrl = "tcp://*:" + config.getProperty("inter.listenPort");
	}

	/**
	 * Starts the interserver.
	 */
	public boolean start() {
		log.info("Starting Bestia Interserver...");

		try {
			startPublisher();
			startSubscriber();
		} catch (ZMQException ex) {
			log.error("Could not start Interserver.", ex);
			stop();
			return false;
		}

		log.info("Interserver started.");
		return true;
	}

	private void startSubscriber() {
		subscriber = context.socket(ZMQ.PULL);
		subscriber.bind(subscriberUrl);
		// Start thread which will process all incoming messages.
		subscriberThread = new MessageSubscriberThread(subscriber);
		subscriberThread.start();

		log.info("Now listening for messages on [{}].", subscriberUrl);
	}

	/**
	 * Starts the message sending capability of the interserver. Incoming messages will be sorted and broadcasted with a
	 * given topic. All interested zone or webserver can listen for these kind of messages and subscribe to them.
	 */
	private void startPublisher() {
		publisher = context.socket(ZMQ.PUB);
		publisher.bind(publishUrl);

		log.info("Now publishing messages on [{}].", publishUrl);
	}

	/**
	 * Stops the interserver.
	 */
	public void stop() {
		log.info("Stopping the Interserver...");

		log.trace("Stopping message processing...");
		// We have to do all the null checks since we may have an stop call during the start and may not have
		// initialized everything.
		if (subscriberThread != null) {
			subscriberThread.isRunning.set(false);
		}

		if (subscriber != null) {
			subscriber.close();
		}

		if (subscriberThread != null) {
			try {
				subscriberThread.join(10000);
			} catch (InterruptedException e) {
				log.warn("Could not shut down subscriberThread gracefully.", e);
			}
		}
		if (publisher != null) {
			publisher.close();
		}
		context.term();

		log.info("Interserver has gone down.");
	}

	public static void main(String[] args) {

		BestiaConfiguration config = new BestiaConfiguration();
		try {
			config.load();
		} catch (IOException ex) {
			log.fatal("Could not load config file. Exiting.", ex);
			System.exit(1);
		}

		final Interserver interserver = new Interserver(config);
		if (!interserver.start()) {
			System.exit(1);
		}

		// Cancel the interserver gracefully when the VM shuts down. Does not
		// work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				interserver.stop();
			}
		});
	}

}
