package net.bestia.messages.dialog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.Message;

public class DialogMessage extends Message {

	private static final long serialVersionUID = 1L;
	public static final String MESSAGE_ID = "ui.dialog";
	
	@JsonProperty("n")
	private final List<DialogNode> nodes;
	
	public DialogMessage(List<DialogNode> nodes) {
		this.nodes = Collections.unmodifiableList(new ArrayList<>(nodes));
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}
