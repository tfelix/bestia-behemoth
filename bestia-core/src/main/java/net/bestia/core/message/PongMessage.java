package net.bestia.core.message;

import net.bestia.core.game.model.Account;

import com.fasterxml.jackson.annotation.JsonProperty;

public class PongMessage extends Message {

	private final String message = "Hello User.";
	private final String messageId = "pong";
	
	public PongMessage(Account account) {
		setAccountId(account.getId());
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
		return String.format("PongMessage[mid: {0}, account id: {1}]", getMessageId(), getAccountId());
	}
}
