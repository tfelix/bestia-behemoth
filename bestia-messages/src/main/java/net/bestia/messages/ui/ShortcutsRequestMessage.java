package net.bestia.messages.ui;

import net.bestia.messages.JsonMessage;

/**
 * Asks the server to replay with a list of shortcuts for the current entity and
 * the account.
 * 
 * @author Thomas Felix
 *
 */
public class ShortcutsRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "shortcuts.request";

	protected ShortcutsRequestMessage() {
		// no op.
	}

	public ShortcutsRequestMessage(long accountId) {
		super(accountId);

		// no op.
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new ShortcutsRequestMessage(getAccountId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}
