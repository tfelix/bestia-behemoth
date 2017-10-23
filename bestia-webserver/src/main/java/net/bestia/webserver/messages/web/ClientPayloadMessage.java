package net.bestia.webserver.messages.web;

import java.util.Objects;

/**
 * Message from a client. Directed towards the server. Must be de-serialized by
 * the socket actor.
 * 
 * @author Thomas Felix
 *
 */
public class ClientPayloadMessage implements SocketMessage {

	private final String uid;
	private final String message;

	public ClientPayloadMessage(String uid, String message) {

		this.uid = Objects.requireNonNull(uid);
		this.message = Objects.requireNonNull(message);
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String getSessionId() {
		return uid;
	}
}
