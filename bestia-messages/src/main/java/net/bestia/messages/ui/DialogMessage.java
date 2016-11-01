package net.bestia.messages.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;
import net.bestia.messages.MessageId;

/**
 * This message will trigger the client to open a dialog NPC box.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DialogMessage extends AccountMessage implements MessageId {

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

	public DialogMessage(List<DialogNode> nodes) {
		this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
