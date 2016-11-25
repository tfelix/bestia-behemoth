package net.bestia.messages.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JasonMessage;

public class PingMessage extends JasonMessage {

	private static final long serialVersionUID = 1L;
	private final static String message = "Hello Bestia.";
	public static final String MESSAGE_ID = "system.ping";
	
	public PingMessage() {
		// no op.
	}
	
	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@JsonProperty("m")
	public String getMessage() {
		return message;
	}
	
	public void setMessage(String message) {
		if(!PingMessage.message.equals(message)) {
			throw new IllegalArgumentException("Wrong message for ping message.");
		}
	}
	
	@Override
	public String toString() {
		return String.format("PingMessage[accId: %d]", getAccountId());
	}
}
