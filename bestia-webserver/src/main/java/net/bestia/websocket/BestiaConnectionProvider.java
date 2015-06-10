package net.bestia.websocket;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.websocket.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;

import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.LoginAuthReplyMessage;
import net.bestia.messages.Message;

/**
 * Helper class to provide a centralized storage for all opend websocket connections and filter the incoming interserver
 * messages for accounts and deliver them via websocket. Basically all shared objects beween the webserver threads
 * should be here. Be aware that calling to methods inside this must be threadsafe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaConnectionProvider implements InterserverMessageHandler {

	private static final Logger log = LogManager.getLogger(BestiaConnectionProvider.class);

	private static BestiaConnectionProvider instance = null;
	private final ObjectMapper mapper = new ObjectMapper();

	private InterserverPublisher publisher;
	private InterserverSubscriber subscriber;
	private final LoginCheckBlocker loginChecker = new LoginCheckBlocker(this);

	private final Map<Long, WebSocket> connections = new ConcurrentHashMap<>();

	/**
	 * Publishes a message to the interserver.
	 * 
	 * @param accountId
	 *            Account id of the accound sending this message.
	 * @param message
	 *            String representation of the message. Must be parsed to an object.
	 * @throws IOException
	 */
	public void publishInterserver(long accountId, String message) throws IOException {
		final Message msg = mapper.readValue(message, Message.class);

		// Regenerate the account id from the server connection.
		msg.setAccountId(accountId);

		publisher.publish(msg);
	}

	public void publishInterserver(Message message) throws IOException {
		publisher.publish(message);
	}

	private void publishClient(Message msg) throws IOException {
		final long accountId = msg.getAccountId();
		if (!connections.containsKey(accountId)) {
			throw new IOException(String.format("No existing connection to account id %d", accountId));
		}

		// Serialize the msg.
		try {
			final String data = mapper.writeValueAsString(msg);
			connections.get(accountId).write(data);
		} catch (NoSuchMethodError ex) {
			log.error("Error while serializing this message.", ex);
		}
	}

	public void addConnection(long accountId, WebSocket socket) {
		connections.put(accountId, socket);

		// Subscribe to the messages.
		subscriber.subscribe("account/" + accountId);
	}

	/**
	 * Unsubscribes from the topic from the zone/interserver and removes this account from our saved connection list.
	 * 
	 * @param accountId
	 *            Account ID to be removed.
	 */
	public void removeConnection(long accountId) {
		subscriber.unsubscribe("account/" + accountId);
		connections.remove(accountId);
	}

	public static void create() {
		instance = new BestiaConnectionProvider();
	}

	public void setup(InterserverPublisher publisher, InterserverSubscriber subscriber) {
		if (instance == null) {
			throw new IllegalStateException("setup() must be called bevor invoking these method.");
		}

		this.publisher = publisher;
		this.subscriber = subscriber;
	}

	/**
	 * Static getter of the InterserverConnectionProvider so it can be retrieved for the socket handler.
	 * 
	 * @return Instance of the InterserverConnectionProvider
	 */
	public static BestiaConnectionProvider getInstance() {
		if (instance == null) {
			throw new IllegalStateException("create() must be called bevor invoking these method.");
		}
		return instance;
	}

	/**
	 * Returns the login check blocker.
	 * 
	 * @return
	 */
	public LoginCheckBlocker getLoginCheckBlocker() {
		return loginChecker;
	}

	/**
	 * Callback when the interserver sends a message.
	 */
	@Override
	public void onMessage(Message msg) {
		// Special case: If the message is a LoginAuthReply message we must route it to the blocker for further
		// processing.
		if (msg.getMessageId().equals(LoginAuthReplyMessage.MESSAGE_ID)) {
			loginChecker.receivedAuthReplayMessage((LoginAuthReplyMessage) msg);
			return;
		}

		// Check the kind of message.
		if (msg.getMessagePath().startsWith("account/")) {
			try {
				// Send the message to the client.
				publishClient(msg);
			} catch (IOException e) {
				log.error("Could not send message to client.", e);
			}
		}
	}
}
