package net.bestia.interserver;

import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZMQException;
import com.netflix.servo.annotations.DataSourceType;
import com.netflix.servo.annotations.Monitor;
import net.bestia.util.BestiaConfiguration;

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

	private static final Logger LOG = LogManager.getLogger(Interserver.class);

	@Monitor(name="MessagesReceived", type=DataSourceType.COUNTER)
	private AtomicLong messagesReceived = new AtomicLong(0);
	
	@Monitor(name="BytesReceived", type=DataSourceType.COUNTER)
	private AtomicLong bytesReceived = new AtomicLong(0);

	/**
	 * This thread processes incoming messages from the zone or the webserver
	 * and puts them into the message queue. The messages will be published
	 * again under a certain path depending on the kind of message so subscriber
	 * can react to the messages.
	 *
	 */
	private class MessageSubscriberThread extends Thread {

		private final Socket subscriber;
		private final Socket publisher;

		public MessageSubscriberThread(Socket subscriber, Socket publisher) {
			this.setName("MessageSubscriberThread");
			this.subscriber = subscriber;
			this.publisher = publisher;
		}

		@Override
		public void run() {
			try {
				subscriber.bind(subscriberUrl);
				LOG.info("Now listening for messages on [{}].", subscriberUrl);
				publisher.bind(publishUrl);
				LOG.info("Now publishing messages on [{}].", publishUrl);
			} catch (ZMQException ex) {
				LOG.error("Could not open sockets.", ex);
				return;
			}

			while (!Thread.currentThread().isInterrupted()) {

				try {
					final String topic = subscriber.recvStr();
					final byte[] data = subscriber.recv();

					LOG.trace("Received message[topic: {}, size {} byte]", topic, data.length);

					// Count the metrics.
					messagesReceived.incrementAndGet();
					bytesReceived.getAndAdd(data.length);

					publisher.sendMore(topic);
					publisher.send(data);

				} catch (ZMQException e) {
					if (e.getErrorCode() == ZMQ.Error.ETERM.getCode()) {
						break;
					}
				}
			}
			subscriber.close();
			publisher.close();
		}
	}

	private MessageSubscriberThread subscriberThread;

	private final Context context;

	private final String publishUrl;
	private final String subscriberUrl;

	private final BestiaConfiguration config;

	/**
	 * Ctor.
	 * 
	 * @param config
	 *            Loaded BestiaConfiguration.
	 */
	public Interserver(BestiaConfiguration config) {
		if (config == null || !config.isLoaded()) {
			throw new IllegalArgumentException("BestiaConfiguration is null or not loaded.");
		}

		context = ZMQ.context(config.getIntProperty("inter.threads"));
		publishUrl = "tcp://" + config.getProperty("inter.domain") + ":" + config.getProperty("inter.publishPort");
		subscriberUrl = "tcp://*:" + config.getProperty("inter.listenPort");

		this.config = config;
	}

	/**
	 * Starts the interserver. Will return true upon success or false if the
	 * server could not be started.
	 * 
	 * @return TRUE if the server has started. FALSE if there was a problem.
	 */
	public boolean start() {

		// Dont start again if we are already running.
		if (subscriberThread != null && subscriberThread.isAlive()) {
			throw new IllegalStateException(
					"Interserver is already running and can not be started again. Call stop() first.");
		}

		LOG.info(config.getVersion());
		LOG.info("Starting Bestia Interserver...");

		Socket subscriber = context.socket(ZMQ.PULL);
		Socket publisher = context.socket(ZMQ.PUB);

		// Start thread which will process all incoming messages.
		subscriberThread = new MessageSubscriberThread(subscriber, publisher);
		subscriberThread.start();

		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			// no op.
		}

		if (!subscriberThread.isAlive()) {
			stop();
			return false;
		}


		LOG.info("Interserver started.");
		return true;
	}

	/**
	 * Stops the interserver.
	 */
	public void stop() {

		LOG.info("Stopping the Interserver...");

		LOG.trace("Stopping message processing...");

		context.close();

		// We have to do all the null checks since we may have an stop call
		// during the start and may not have
		// initialized everything.
		if (subscriberThread != null) {
			try {
				subscriberThread.interrupt();
				subscriberThread.join();
			} catch (InterruptedException e) {
				LOG.warn("Could not shut down subscriberThread gracefully.", e);
			}
		}

		LOG.info("Interserver has gone down.");
	}
}
