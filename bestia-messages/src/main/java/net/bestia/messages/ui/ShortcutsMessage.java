package net.bestia.messages.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;
import net.bestia.messages.JsonMessage;
import net.bestia.model.domain.Shortcut.ShortcutJson;

/**
 * Sends the answer of the server containing the shortcuts for the current
 * active bestia to the client.
 * 
 * @author Thomas Felix
 *
 */
public class ShortcutsMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	private static final String MESSAGE_ID = "shortcuts";
	
	@JsonProperty("as")
	private List<ShortcutJson> accountShortcuts = new ArrayList<>();
	
	@JsonProperty("bs")
	private List<ShortcutJson> bestiaShortcuts = new ArrayList<>();

	protected ShortcutsMessage() {
		// no op.
	}

	public ShortcutsMessage(long accountId, long entityId) {
		super(accountId, entityId);
		
		// no op.
	}
	
	public void addAccountShortcuts(Collection<ShortcutJson> accShortcuts) {
		accountShortcuts.addAll(accShortcuts);
	}
	
	public void addBestiaShortcuts(Collection<ShortcutJson> bestiaShortcuts) {
		bestiaShortcuts.addAll(bestiaShortcuts);
	}

	@Override
	public JsonMessage createNewInstance(long accountId) {
		return new ShortcutsMessage(getAccountId(), getEntityId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

}
