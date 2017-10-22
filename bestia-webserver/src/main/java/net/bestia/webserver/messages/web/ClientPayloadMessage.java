package net.bestia.webserver.messages.web;

import java.util.Objects;

public class ClientPayloadMessage {

	private final String uid;
	private final String message;

	public ClientPayloadMessage(String uid, String message) {

		this.uid = Objects.requireNonNull(uid);
		this.message = Objects.requireNonNull(message);
	}

	public String getUid() {
		return uid;
	}

	public String getMessage() {
		return message;
	}
}
