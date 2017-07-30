package net.bestia.messages.misc;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Answer to a {@link PingMessage} from the client.
 * 
 * @author Thomas Felix
 *
 */
public class PongMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "lat.res";
	
	@JsonProperty("s")
	private long start;

	/**
	 * Ctor.
	 */
	protected PongMessage() {
		// no op.
	}

	public PongMessage(long accId) {
		super(accId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	public long getStart() {
		return start;
	}
	
	public void setStart(long start) {
		this.start = start;
	}

	@Override
	public String toString() {
		return String.format("PongMessage[account id: %d]", getAccountId());
	}

	@Override
	public PongMessage createNewInstance(long accountId) {
		return new PongMessage(accountId);
	}
}
