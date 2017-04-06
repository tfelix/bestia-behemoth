package net.bestia.messages.ui;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * Rquests shortcuts for the currently selected bestia.
 * 
 * @author Thomas Felix
 *
 */
public class ShortcutsMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	
	private static final String MESSAGE_ID = "shortcuts";
	
	@JsonProperty("sc")
	private String shortcuts;
	
	public ShortcutsMessage(long accountId) {
		super(accountId);
	}

	public ShortcutsMessage(String shortcuts) {
		
		this.shortcuts = Objects.requireNonNull(shortcuts);
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
