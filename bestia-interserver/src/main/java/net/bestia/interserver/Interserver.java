package net.bestia.interserver;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

/**
 * The Interserver builds the bestia system backbone. It will receive messages
 * from the webserver and will relay this information to the zone server. These
 * are able to subscribe to account topics which will then receive all
 * communication from this account.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class Interserver {

	private final static Logger log = LogManager.getLogger(Interserver.class);

	/**
	 * This thread processes incoming messages from the zone or the webserver
	 * and puts them into the message queue. The messages will be published
	 * again under a certain path depending on the kind of message so subscriber
	 * can react to the messages.
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
			while (isRunning.get()) {
				try {
					byte[] data = subscriber.recv();
					log.trace("Received message of {} byte.", data.length);
					messageQueue.put(data);
				} catch (InterruptedException ex) {
					// no op.
				}
			}
		}

	}

	/**
	 * Responsible for sending messages to the publishing bus.
	 *
	 */
	private class MessagePublisherThread extends Thread {

		private final Socket publisher;
		public final AtomicBoolean isRunning = new AtomicBoolean(true);

		public MessagePublisherThread(Socket publisher) {
			this.setName("MessagePublisherThread");
			this.publisher = publisher;
		}

		private String getTopic(Message msg) {
			return "zone/all";
		}

		@Override
		public void run() {
			while (isRunning.get() || messageQueue.size() > 0) {
				try {
					final byte[] data = messageQueue.take();
					final Message msg = (Message) ObjectSerializer
							.deserializeObject(data);
					log.trace("Received message: {}", msg.toString());
					final String topicName = getTopic(msg);

					publisher.sendMore(topicName);
					publisher.send(data);
				} catch (InterruptedException | IOException
						| ClassNotFoundException ex) {
					// no op.
				}
			}

			log.trace("MessagePublisherThread has ended.");
		}

	}

	private MessageSubscriberThread subscriberThread;
	private MessagePublisherThread publisherThread;

	private Context context;
	private Socket publisher;
	private Socket subscriber;

	private BlockingQueue<byte[]> messageQueue = new LinkedBlockingQueue<>();
	private final String publishUrl;
	private final String subscriberUrl;

	/**
	 * 
	 * @param config
	 *            Loaded BestiaConfiguration.
	 */
	public Interserver(BestiaConfiguration config) {
		if (!config.isLoaded()) {
			throw new IllegalArgumentException(
					"BestiaConfiguration is not loaded.");
		}

		context = ZMQ.context(config.getIntProperty("inter.threads"));
		publishUrl = "tcp://localhost:"
				+ config.getProperty("inter.publishPort");
		subscriberUrl = "tcp://*:" + config.getProperty("inter.listenPort");
	}

	/**
	 * Starts the interserver.
	 */
	public void start() {
		log.info("Starting Bestia Interserver...");

		startPublisher();
		startSubscriber();

		log.info("Interserver started.");
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
	 * Starts the message sending capability of the interserver. Incoming
	 * messages will be sorted and broadcasted with a given topic. All
	 * interested zone or webserver can listen for these kind of messages and
	 * subscribe to them.
	 */
	private void startPublisher() {
		publisher = context.socket(ZMQ.PUB);
		publisher.bind(publishUrl);

		// Start thread which will publish all received messages.
		publisherThread = new MessagePublisherThread(publisher);
		publisherThread.start();
		
		log.info("Now publishing messages on [{}].", publishUrl);
	}

	/**
	 * Stops the interserver.
	 */
	public void stop() {
		log.info("Stopping the Interserver...");

		log.trace("Stopping incoming messages...");
		subscriberThread.isRunning.set(false);
		subscriber.close();
		try {
			subscriberThread.join(10000);
		} catch (InterruptedException e) {
			log.warn("Could not shut down subscriberThread gracefully.", e);
		}

		log.trace("Stopping the message processing...");
		publisherThread.isRunning.set(false);
		try {
			publisherThread.join(10000);
		} catch (InterruptedException e) {
			log.warn("Could not shut down publisherThread gracefully.", e);
		}
		publisher.close();
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
		interserver.start();

		// Cancel the interserver gracefully when the VM shuts down. Does not
		// work properly on windows machines.
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				interserver.stop();
			}
		});
	}

}
