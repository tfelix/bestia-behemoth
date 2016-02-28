package net.bestia.messages.ui;

import net.bestia.messages.Message;

/**
 * This message can be used to display a message in the client. Currently there
 * are the following types of messages:
 * <ul>
 * <li>Dialogs</li>
 * <li>Shop Displays</li>
 * <li></li>
 * </ul>
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class UIDisplayDialogMessage extends Message {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "ui.display.dialog";
	
	private String text;

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getClientMessagePath();
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}

}
