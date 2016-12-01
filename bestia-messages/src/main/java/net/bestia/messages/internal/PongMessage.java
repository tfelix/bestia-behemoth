package net.bestia.messages.internal;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.JsonMessage;

public class PongMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	private static final String message = "Hello User.";
	private static final String MESSAGE_ID = "system.pong";
	
	/**
	 * Ctor.
	 */
	public PongMessage() {
		// no op.
	}
	
	public PongMessage(AccountMessage msg) {
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
		return String.format("PongMessage[account id: %d]", getAccountId());
	}
}
