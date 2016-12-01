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
	private final int playerBestiaId;
	
	/**
	 * Ctor.
	 */
	public BestiaActivateMessage() {
		this(0, 0);
	}
	
	/**
	 * Ctor.
	 */
	public BestiaActivateMessage(long accId, int bestiaId) {
		super(accId);
		this.playerBestiaId = bestiaId;
	}
	
	public int getPlayerBestiaId() {
		return playerBestiaId;
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
