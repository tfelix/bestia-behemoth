package net.bestia.webserver.bestia;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import net.bestia.core.connection.ConnectionState;
import net.bestia.core.message.LogoutMessage;
import net.bestia.core.message.Message;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

/**
 * Holds the connection to a connected bestia player.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 * 
 */
public class BestiaSocket {

	/*
	private static final Logger log = LogManager.getLogger(BestiaSocket.class);
	private static final ObjectMapper mapper = new ObjectMapper();
	private static final BestiaWebsocketConnector connections = BestiaWebsocketConnector
			.getInstance();
	static {
		// Configure the Jackson mapper.
		// to enable standard indentation ("pretty-printing"):
		mapper.enable(SerializationFeature.INDENT_OUTPUT);
		// to allow serialization of "empty" POJOs (no properties to serialize)
		// (without this setting, an exception is thrown in those cases)
		mapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
		// to write java.util.Date, Calendar as number (timestamp):
		mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

		// DeserializationFeature for changing how JSON is read as POJOs:
		// to prevent exception when encountering unknown property:
		mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
		// to allow coercion of JSON empty String ("") to null Object value:
		mapper.enable(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT);
	}

	private Session session;
	private int accountId;
	private String uuid = UUID.randomUUID().toString();
	private ConnectionState state;


	public BestiaSocket() {
		// no op.
	}



	public void setState(ConnectionState state) {
		this.state = state;
	}

	@Override
	public void onWebSocketConnect(Session session) {
		super.onWebSocketConnect(session);
		log.trace("WS connection opened from {}", session.getRemoteAddress());

		this.session = session;

		// If this state is set to elevated then the socket is now fully
		// connected to the bestia game server and can send and receive
		// messages.
		connections.addConnection(uuid, this);
	}

	@Override
	public void onWebSocketText(String message) {
		super.onWebSocketText(message);
		log.trace("Received: {}", message);

		try {
			// Take the message and try to parse it into a bestia game message
			// object.
			Message msg = mapper.readValue(message, Message.class);

			// Set the account id which is hold by the connection.
			// Or is 0 if the account is not yet logged in.
			msg.setAccountId(accountId);

			if (state == ConnectionState.NEW) {
				// Save the uuid to the message object
				// so the server can reference the connection.
				((RequestLoginMessage) msg).setUuid(uuid);
			}

			// Transform the message into a command object and let the bestia
			// server execute it.
			connections.getZoneserver().handleMessage(msg);

		} catch (IOException e) {
			log.error(e.getMessage());
		}
	}

	/**
	 * Closes a websocket session in case of an error etc. Does also additional
	 * cleanup work.

	private void close() {
		if (session.isOpen()) {
			session.close();
		}
		connections.removeConnection(this);
	}

	@Override
	public void onWebSocketClose(int statusCode, String reason) {
		super.onWebSocketClose(statusCode, reason);
		System.out.println("Socket closed: [" + statusCode + "] " + reason);
		close();
	}

	@Override
	public void onWebSocketError(Throwable cause) {
		super.onWebSocketError(cause);
		cause.printStackTrace(System.err);
	}

	public void sendMessage(Message message) throws IOException {

		if (!session.isOpen()) {
			throw new IOException(String.format(
					"Session to acc_id: {} is closed.", accountId));
		}

		final RemoteEndpoint remote = session.getRemote();

		try {
			String jsonMsg = mapper.writeValueAsString(message);
			log.trace("Sending: {} to {}", jsonMsg, session.getRemoteAddress());
			Future<Void> msgSend = remote.sendStringByFuture(jsonMsg);
			
			if (message.getMessageId().equals(LogoutMessage.MESSAGE_ID)) {
				// Close the session after a the logout has been send.
				// TODO das geschickter lösen ohne blocken.
				try {
					msgSend.get();
				} catch (InterruptedException | ExecutionException e) {
					session.close();
				}
				
			}
			
		} catch (JsonProcessingException e) {
			log.error(e.getMessage());
		}
	}*/
}
