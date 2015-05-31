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
import net.bestia.messages.Message;

public class BestiaConnectionProvider implements InterserverMessageHandler {
	
	private static final Logger log = LogManager.getLogger(BestiaConnectionProvider.class);

	private static BestiaConnectionProvider instance = null;
	private final ObjectMapper mapper = new ObjectMapper();
	
	private InterserverPublisher publisher;
	private InterserverSubscriber subscriber;
	
	private final Map<Integer, WebSocket> connections = new ConcurrentHashMap<>();

	public void publishInterserver(String message) throws IOException {

		final Message msg = mapper.readValue(message, Message.class);
		
		// TODO das hier ausbauen.
		msg.setAccountId(1);

		publisher.publish(msg);
	}

	public void publishInterserver(Message message) throws IOException {
		
		publisher.publish(message);
		
	}

	public void publishClient(Message msg) throws IOException {
		final int accountId = msg.getAccountId();
		if (!connections.containsKey(accountId)) {
			throw new IOException(String.format("No existing connection to account id %d", accountId));
		}

		// Serialize the msg.
		final String data = mapper.writeValueAsString(msg);
		connections.get(accountId).write(data);
	}

	public void addConnection(int accountId, WebSocket socket) {
		connections.put(accountId, socket);
		
		// Subscribe to the messages.
		subscriber.subscribe("account/" + accountId);
	}

	public void removeConnection(int accountId) {
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
	 * Callback when the interserver sends a message.
	 */
	@Override
	public void onMessage(Message msg) {
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

	/**
	 * Callback when the interserver connection has been lost.
	 */
	@Override
	public void connectionLost() {
		// TODO Auto-generated method stub

	}
}
