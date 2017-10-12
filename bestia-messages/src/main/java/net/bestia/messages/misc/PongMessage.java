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


	public PongMessage(long accId, long start) {
		super(accId);
		
		this.start = start;
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
		return String.format("PongMessage[account id: %d, stamp: %d]", getAccountId(), start);
	}

	@Override
	public PongMessage createNewInstance(long accountId) {
		return new PongMessage(accountId, start);
	}
}
