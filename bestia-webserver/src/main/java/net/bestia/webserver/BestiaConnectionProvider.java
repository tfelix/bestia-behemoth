package net.bestia.webserver;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.atmosphere.websocket.WebSocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.servo.monitor.BasicGauge;
import com.netflix.servo.monitor.Counter;
import com.netflix.servo.monitor.Gauge;
import com.netflix.servo.monitor.MonitorConfig;
import com.netflix.servo.monitor.Monitors;

import net.bestia.interserver.InterserverMessageHandler;
import net.bestia.interserver.InterserverPublisher;
import net.bestia.interserver.InterserverSubscriber;
import net.bestia.messages.AccountMessage;
import net.bestia.messages.Message;
import net.bestia.messages.login.LoginAuthReplyMessage;
import net.bestia.messages.login.LogoutBroadcastMessage;

/**
 * Helper class to provide a centralized storage for all opend websocket
 * connections and filter the incoming interserver messages for accounts and
 * deliver them via websocket. Basically all shared objects between the
 * webserver threads should be here. Be aware that calling to methods inside
 * this must be threadsafe.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaConnectionProvider implements InterserverMessageHandler {
	
	private class WebConnection {
		public final WebSocket socket;
		public final String token;
		
		public WebConnection(WebSocket socket, String token) {
			this.socket = socket;
			this.token = token;
		}
	}

	private static final Logger log = LogManager.getLogger(BestiaConnectionProvider.class);
	
	
	@SuppressWarnings("unused")
	private final Gauge<Integer> loginQueueSize = new BasicGauge<>(MonitorConfig.builder("LoginQueueSize").build(), new Callable<Integer>() {
		@Override
		public Integer call() throws Exception {
			return connections.size();
		}
	});
	@SuppressWarnings("unused")
	private final Counter loginMessageMetric = Monitors.newCounter("LoginMessages");

	private final ObjectMapper mapper = new ObjectMapper();

	private final InterserverPublisher publisher;
	private final InterserverSubscriber subscriber;

	private final LoginCheckBlocker loginChecker = new LoginCheckBlocker(this);

	private final Map<Long, WebConnection> connections = new ConcurrentHashMap<>();

	/**
	 * Ctor. Must have handler to send and receive messages from the
	 * interserver.
	 * 
	 * @param publisher
	 * @param subscriber
	 */
	public BestiaConnectionProvider(InterserverPublisher publisher, InterserverSubscriber subscriber) {
		if (publisher == null) {
			throw new IllegalArgumentException("Publisher can not be null.");
		}
		if (subscriber == null) {
			throw new IllegalArgumentException("Subscriber can not be null.");
		}

		this.publisher = publisher;
		this.subscriber = subscriber;
	}

	/**
	 * Publishes a raw string message to the interserver. The string must be
	 * parsed (it should be JSON) into a real java message. It will then be
	 * send
	 * 
	 * @param accountId
	 *            Account id of the accound sending this message.
	 * @param message
	 *            String representation of the message. Must be parsed to an
	 *            object.
	 * @throws IOException
	 */
	public void publishInterserver(long accountId, String message) throws IOException {
		log.trace("Publish message to interserver: {}", message);

		final AccountMessage msg = mapper.readValue(message, AccountMessage.class);

		// Regenerate the account id from the server connection.
		msg.setAccountId(accountId);

		publisher.publish(msg);
	}

	public void publishInterserver(Message message) throws IOException {
		log.trace("Publish message to interserver: {}", message.toString());
		publisher.publish(message);
	}

	private void publishClient(AccountMessage msg) throws IOException {
		final long accountId = msg.getAccountId();
		if (!connections.containsKey(accountId)) {
			throw new IOException(String.format("No existing connection to account id %d", accountId));
		}

		// Serialize the msg.
		try {
			final String data = mapper.writeValueAsString(msg);
			log.trace("Sending message: {}", data);
			connections.get(accountId).socket.write(data);
		} catch (NoSuchMethodError ex) {
			log.error("Error while serializing this message.", ex);
		}
	}

	public void addConnection(long accountId, WebSocket socket, String token) {
		connections.put(accountId, new WebConnection(socket, token));

		// Subscribe to the messages.
		subscriber.subscribe("account/" + accountId);
	}

	/**
	 * Unsubscribes from the topic from the zone/interserver and removes this
	 * account from our saved connection list.
	 * 
	 * @param accountId
	 *            Account ID to be removed.
	 */
	public void removeConnection(long accountId) {
		log.debug("Removed connection to account id: {}", accountId);
		subscriber.unsubscribe("account/" + accountId);
		connections.remove(accountId);
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

		log.trace("Received message from interserver: {}", msg.toString());

		// Special case: If the message is a LoginAuthReply message we must
		// route it to the blocker for further processing.
		final String messageId = msg.getMessageId();
		if (messageId.equals(LoginAuthReplyMessage.MESSAGE_ID)) {
			loginChecker.receivedAuthReplayMessage((LoginAuthReplyMessage) msg);
			return;
		} else if(messageId.equals(LogoutBroadcastMessage.MESSAGE_ID)) {
			
			// Are we responsible for this account?
			if(!connections.containsKey(((AccountMessage) msg).getAccountId())) {
				return;
			}
			
			// Log out the client if the token matches or is null.
			final LogoutBroadcastMessage logoutMsg = (LogoutBroadcastMessage) msg;
			
			if(logoutMsg.getToken() != null) {
				// Logout the connection if token matches.
				final String usedToken = connections.get(logoutMsg.getAccountId()).token;
				if(usedToken.equals(logoutMsg.getToken())) {
					connections.get(logoutMsg.getAccountId()).socket.close();
					removeConnection(logoutMsg.getAccountId());
				}
			} else {
				// Just cancel the connection.
				connections.get(logoutMsg.getAccountId()).socket.close();
				removeConnection(logoutMsg.getAccountId());
			}
			return;
		}

		// Check the kind of message.
		if (msg.getMessagePath().startsWith("account/")) {
			try {
				// Send the message to the client.
				publishClient((AccountMessage) msg);
			} catch (IOException e) {
				log.error("Could not send message to client.", e);
			}
		}
	}
}
