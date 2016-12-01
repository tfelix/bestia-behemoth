package net.bestia.messages.bestia;

import net.bestia.messages.JsonMessage;

/**
 * This message is send from the client to ask the server for data about the
 * owned bestias and their status.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class RequestBestiaInfoMessage extends JsonMessage {
	
	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "bestia.requestinfo";

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
	
	@Override
	public String toString() {
		return String.format("RequestBestiaInfoMessage[accId: %d]", getAccountId());
	}
}
