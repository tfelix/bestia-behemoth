package net.bestia.messages.bestia;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;

/**
 * This message is send from the client to ask the server for data about the
 * owned bestias and their status.
 * 
 * @author Thomas Felix
 *
 */
public class BestiaInfoRequestMessage extends AccountMessage implements MessageId {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "bestia.requestinfo";

	/**
	 * Needed for MessageTypeIdResolver
	 */
	private BestiaInfoRequestMessage() {
		super(0);
	}

	public BestiaInfoRequestMessage(long accId) {
		super(accId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("RequestBestiaInfoMessage[accId: %d]", getAccountId());
	}

	@Override
	public BestiaInfoRequestMessage createNewInstance(long accountId) {
		return new BestiaInfoRequestMessage(accountId);
	}
}
