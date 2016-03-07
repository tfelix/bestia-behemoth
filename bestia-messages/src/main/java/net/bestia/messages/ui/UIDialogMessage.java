package net.bestia.messages.ui;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.AccountMessage;

/**
 * This message is send to the client in order to display a dialog to the user.
 * There are different types of dialogs which are given by the dialog type.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class UIDialogMessage extends AccountMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "ui.dialog";

	@JsonProperty("did")
	private String dialogId;

	@JsonProperty("t")
	private String text;

	/**
	 * A hint to preload this images since they are probably needed in this
	 * conversation with the NPC.
	 */
	@JsonProperty("imgPre")
	private List<String> imagePreload;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

}
