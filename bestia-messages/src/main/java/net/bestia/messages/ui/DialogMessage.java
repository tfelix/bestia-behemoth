package net.bestia.messages.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * This message will trigger the client to open a dialog NPC box.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DialogMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "ui.dialog";

	@JsonProperty("n")
	private final List<DialogNode> nodes;

	/**
	 * Std. ctor.
	 */
	public DialogMessage() {
		this.nodes = Collections.unmodifiableList(new ArrayList<>());
	}

	public DialogMessage(long accId, List<DialogNode> nodes) {
		super(accId);
		
		this.nodes = Collections.unmodifiableList(Objects.requireNonNull(new ArrayList<>(nodes)));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String toString() {
		return String.format("DialogMsg[nodes: %s]", nodes.toString());
	}

	@Override
	public DialogMessage createNewInstance(long accountId) {
		return new DialogMessage(accountId, nodes);
	}
}
