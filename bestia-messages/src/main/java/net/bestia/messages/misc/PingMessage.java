package net.bestia.messages.misc;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Simple ping message which can be send to the server. Will be answered with a
 * {@link PongMessage}.
 * 
 * @author Thomas Felix
 *
 */
public class PingMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "lat.req";
	
	@JsonProperty("s")
	private long start;

	protected PingMessage() {
		// no op.
	}

	public PingMessage(long accId) {
		super(accId);
		
		start = System.currentTimeMillis();
	}

	public PingMessage(long accountId, long currentTimeMillis) {
		super(accountId);
		
		this.start = currentTimeMillis;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("PingMessage[accId: %d, stamp: %d]", getAccountId(), start);
	}

	@Override
	public PingMessage createNewInstance(long accountId) {
		return new PingMessage(accountId);
	}
}
