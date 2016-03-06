package net.bestia.messages.dialog;

import java.io.Serializable;

/**
 * These nodes will be read by the client software which will then decide how to
 * display this information.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DialogNode implements Serializable {

	private static final long serialVersionUID = 1L;
	private final DialogType type;
	private final String data;

	public DialogNode(DialogType type, String data) {
		this.type = type;
		this.data = data;
	}

	public String getData() {
		return data;
	}

	public DialogType getType() {
		return type;
	}
}
