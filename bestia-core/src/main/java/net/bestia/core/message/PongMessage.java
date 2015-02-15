package net.bestia.core.message;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PongMessage extends Message {

	private static final String message = "Hello User.";
	private static final String messageId = "pong";
	
	public PongMessage(int accountId) {
		setAccountId(accountId);
	}
	
	@JsonProperty("m")
	public String getMessage() {
		return message;
	}

	@Override
	public String getMessageId() {
		return messageId;
	}

	@Override
	public String toString() {
		return String.format("PongMessage[mid: %s, account id: %d]", getMessageId(), getAccountId());
	}
}
