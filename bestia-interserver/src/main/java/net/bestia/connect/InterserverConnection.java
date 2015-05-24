package net.bestia.connect;

import java.io.IOException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Context;
import org.zeromq.ZMQ.Socket;

import net.bestia.connect.AnnounceMessage.Type;
import net.bestia.messages.Message;

/**
 * This class provides a facade to the interserver connection.
 * 
 * @author Thomas
 *
 */
public final class InterserverConnection {

	private final static Logger log = LogManager.getLogger(InterserverConnection.class);

	public interface InterserverConnectionHandler {
		/**
		 * Is called if a message is received from the interserver.
		 * 
		 * @param msg
		 */
		public void onMessage(Message msg);
	}

	private final InterserverConnectionHandler handler;
	private final Socket subscriber;
	private final Socket publisher;
	private final String publishUrl;
	private final String interserverUrl;
	private final Context ctx;
	private final String name;

	public InterserverConnection(String name, InterserverConnectionHandler handler, Context ctx, String publishUrl) {
		this.handler = handler;

		this.name = name;
		this.ctx = ctx;
		subscriber = ctx.socket(ZMQ.SUB);
		publisher = ctx.socket(ZMQ.PUB);

		this.publishUrl = publishUrl;
		this.interserverUrl = "";
	}

	public void startPublisher() {
		publisher.bind(publishUrl);
	}

	public void connectSubscriber(String subscribeUrl) {
		subscriber.connect(subscribeUrl);
		// Subscribe only to special topics.

		// Read message contents
		byte[] data = subscriber.recv(0);
		Message msg = (Message) ObjectSerializer.deserializeObject(data);

		handler.onMessage(msg);
	}

	public void connect() throws IOException {
		// Start our own publisher.
		startPublisher();

		// Do the announcement/handshake.
		String subscribeUrl = startHandshake();
		log.debug("Handshake completed. Got subscribe URL: {}", subscribeUrl);

		connectSubscriber(subscribeUrl);
	}

	/**
	 * 
	 * @return Subscriber URL to which we can subscribe.
	 * @throws IOException
	 *             If something during the handshake with the interserver goes wrong.
	 */
	private String startHandshake() throws IOException {
		// Prepare our context and publisher
		final Socket requester = ctx.socket(ZMQ.REQ);

		requester.setLinger(0);
		requester.setSendTimeOut(10000);
		requester.setReceiveTimeOut(10000);

		requester.connect(interserverUrl);

		// Prepare the request message.
		AnnounceWebserverMessage msg = AnnounceWebserverMessage.getRequestMessage(name, publishUrl);

		try {
			byte[] data = ObjectSerializer.serializeObject(msg);
			requester.send(data, 0);
			data = requester.recv(0);
			AnnounceWebserverMessage replyMsg = (AnnounceWebserverMessage) ObjectSerializer.deserializeObject(data);

			if (replyMsg.getType() != Type.REPLY_ACK) {
				throw new IOException("Interserver denies join.");
			}
			requester.close();
			return replyMsg.getSubscribeUrl();

		} catch (ClassNotFoundException ex) {
			log.error("Error while connecting with interserver.", ex);
			requester.close();
			throw new IOException("Could not connect with the interserver.", ex);
		}
	}

	public void disconnect() {
		subscriber.close();
		publisher.close();
	}
}
