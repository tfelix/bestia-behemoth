package net.bestia.interserver;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import net.bestia.messages.Message;
import net.bestia.util.BestiaConfiguration;

/**
 * This class provides a facade to the Interserver. Can be used by modules who
 * want to connect to the interserver.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public final class InterserverConnection {

	private final static Logger log = LogManager
			.getLogger(InterserverConnection.class);

	/**
	 * Defined callbacks for Interserver events. The facade will use this as
	 * callbacks.
	 *
	 */
	public interface InterserverConnectionHandler {
		/**
		 * Is called if a message is received from the interserver.
		 * 
		 * @param msg
		 */
		public void onMessage(Message msg);

		/**
		 * Handler gets called if a connection to be interserver is lost and can
		 * not be reestablished.
		 */
		public void connectionLost();
	}

	/**
	 * This thread will consume the message queue and send out the messages to
	 * the interserver.
	 *
	 */
	private final class MessageConsumerThread extends Thread {

		private final Socket publisher;

		public MessageConsumerThread(Socket publisher) {
			this.setName("MessageConsumerThread");
			this.publisher = publisher;
		}

		public AtomicBoolean isRunning = new AtomicBoolean(true);

		@Override
		public void run() {
			while (isRunning.get() || messageQueue.size() > 0) {
				try {
					Message msg = messageQueue.take();
					byte[] data = ObjectSerializer.serializeObject(msg);
					publisher.send(data);
				} catch (InterruptedException | IOException ex) {

				}
			}
			log.trace("MessageConsumerThread has ended.");
		}
	}

	private final InterserverConnectionHandler handler;
	private final Socket subscriber;
	private final Socket publisher;
	private final String publishUrl;
	private final Context ctx;
	/**
	 * Threadsafe flag if we are already connected since this class must be
	 * thread safe.
	 */
	private final AtomicBoolean isConnected = new AtomicBoolean(false);
	private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();
	private MessageConsumerThread consumerThread;
	private String subscribeUrl;

	/**
	 * Maintains a persisted connection to the interserver. This communication
	 * backbone is essential for the operation of the webserver. If the
	 * connections gets dropped the webserver is headless and without function.
	 * It will cease to operate without backbone connection.
	 * 
	 * @param name
	 * @param handler
	 * @param config
	 *            A loaded BestiaConfiguration object.
	 */
	public InterserverConnection(String name,
			InterserverConnectionHandler handler, String publishUrl,
			BestiaConfiguration config) {
		this.handler = handler;

		this.ctx = ZMQ.context(1);
		subscriber = ctx.socket(ZMQ.SUB);
		publisher = ctx.socket(ZMQ.PUSH);

		// Generate the interserver url from the config.
		this.subscribeUrl = config.getDomainPortString("inter.domain",
				"inter.listenPort", "tcp://");
		this.publishUrl = publishUrl;
	}

	private void startPublisher() {
		publisher.connect(publishUrl);

		// Start the thread so message sending can occure.
		consumerThread = new MessageConsumerThread(publisher);
		consumerThread.start();
	}

	private void connectSubscriber(String subscribeUrl) {
		subscriber.connect(subscribeUrl);
		// TODO Subscribe only to special topics.

		// Read message contents. Must be done in seperate thread.
		/*
		 * byte[] data = subscriber.recv(0); Message msg; try { msg = (Message)
		 * ObjectSerializer.deserializeObject(data); handler.onMessage(msg); }
		 * catch (ClassNotFoundException | IOException e) {
		 * log.error("Could not deserialize message.", e); }
		 */
	}

	/**
	 * Sends a message to the interserver. This method is threadsafe.
	 * 
	 * @param msg
	 *            Message to send to the bestia interserver.
	 */
	public void sendMessage(Message msg) {
		if (!isConnected.get()) {
			log.warn("Closed connection. No more messages will be send.");
			return;
		}
		try {
			messageQueue.put(msg);
		} catch (InterruptedException e) {
			// no op.
		}
	}

	public synchronized void connect() throws IOException {
		if (isConnected.getAndSet(true) == true) {
			log.warn("Already connected.");
			return;
		}
		log.info("Connecting to interserver...");

		// Start our own publisher.
		startPublisher();

		// Connect the subscriber to the interserver.
		// The interserver should also connect to us aswell.
		connectSubscriber(subscribeUrl);

		log.info("Connected to interserver.");
	}

	/**
	 * Disconnects from the interserver.
	 */
	public synchronized void disconnect() {
		// Close the queue so no more messages are accepted.
		isConnected.set(false);

		// Shutdown the messaging threads.
		// It will end as soon as the message queue is processed and empty.
		consumerThread.isRunning.set(false);
		try {
			// Wait for 10 seconds.
			consumerThread.join(10000);
		} catch (InterruptedException e) {
			log.warn("Consumerthread could not be shut down gracefully.", e);
		}

		// Close the sockets.
		subscriber.close();
		publisher.close();

		log.debug("Disconnected from interserver.");
	}
}