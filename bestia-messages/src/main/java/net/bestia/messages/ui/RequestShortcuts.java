package net.bestia.messages.ui;

import net.bestia.messages.JsonMessage;

/**
 * Rquests shortcuts for the currently selected bestia.
 * 
 * @author Thomas Felix
 *
 */
public class RequestShortcuts extends JsonMessage {

	private static final long serialVersionUID = 1L;
	
	private static final String MESSAGE_ID = "shortcuts.request";
	
	public RequestShortcuts(long accountId) {
		super(accountId);
	}

	protected RequestShortcuts() {
		// no op.
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new ShortcutsMessage(accountId);
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}
