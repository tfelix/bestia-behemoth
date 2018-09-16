package net.bestia.messages.client;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;

/**
 * Simple ping message which can be send to the server. Will be answered with a
 * {@link PongMessage}.
 * 
 * @author Thomas Felix
 *
 */
public class PingMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "lat.req";

	private final long start;

	/**
	 * Needed for MessageTypeIdResolver
	 */
	private PingMessage() {
		super(0);

		start = 0;
	}

	public PingMessage(long accId) {
		super(accId);

		start = System.currentTimeMillis();
	}

	public PingMessage(long accountId, long currentTimeMillis) {
		super(accountId);

		this.start = currentTimeMillis;
	}
	
	@JsonProperty("s")
	public long getStart() {
		return start;
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
