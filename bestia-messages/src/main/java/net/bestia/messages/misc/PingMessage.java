package net.bestia.messages.misc;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

public class PingMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	private final static String message = "Hello Bestia.";
	public static final String MESSAGE_ID = "system.ping";
	
	protected PingMessage() {
		// no op.
	}
	
	public PingMessage(long accId) {
		super(accId);
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

	@Override
	public PingMessage createNewInstance(long accountId) {
		return new PingMessage(accountId);
	}
}
