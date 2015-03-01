package net.bestia.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PongMessage extends Message {

	private static final String message = "Hello User.";
	private static final String MESSAGE_ID = "system.pong";
	
	/**
	 * Ctor.
	 */
	public PongMessage() {
		// no op.
	}
	
	public PongMessage(Message msg) {
		super(msg);
	}
	
	@JsonProperty("m")
	public String getMessage() {
		return message;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("PongMessage[mid: %s, account id: %d]", getMessageId(), getAccountId());
	}
}
