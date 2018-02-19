package bestia.messages.ui;

import java.io.Serializable;

/**
 * These nodes will be read by the client software which will then decide how to
 * display this information.
 * 
 * @author Thomas Felix
 *
 */
public class DialogNode implements Serializable {

	private static final long serialVersionUID = 1L;
	private final DialogAction type;
	private final String data;

	public DialogNode(DialogAction type, String data) {
		this.type = type;
		this.data = data;
	}

	public String getData() {
		return data;
	}

	public DialogAction getType() {
		return type;
	}
}
