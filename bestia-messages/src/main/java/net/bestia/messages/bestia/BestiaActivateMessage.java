package net.bestia.messages.bestia;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Client sends this message if it wants to switch to another active bestia.
 * This bestia from now on is responsible for gathering all visual information.
 * And the client will get updated about these data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivateMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activate";

	@JsonProperty("pbid")
	private final long playerBestiaId;

	/**
	 * Priv. ctor for jackson.
	 */
	protected BestiaActivateMessage() {
		this(0, 0);
	}

	/**
	 * Ctor.
	 */
	public BestiaActivateMessage(long accId, long bestiaId) {
		super(accId);
		this.playerBestiaId = bestiaId;
	}

	public long getPlayerBestiaId() {
		return playerBestiaId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public BestiaActivateMessage createNewInstance(long accountId) {
		final BestiaActivateMessage msg = new BestiaActivateMessage(accountId, playerBestiaId);
		return msg;
	}

	@Override
	public String toString() {
		return String.format("BestiaActivateMessage[accId: %d, bestiaId: %d]", getAccountId(), getPlayerBestiaId());
	}
}
