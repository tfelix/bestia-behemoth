package net.bestia.zoneserver.script;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.dialog.DialogMessage;
import net.bestia.messages.dialog.DialogNode;
import net.bestia.messages.dialog.DialogType;

/**
 * The {@link DialogBuilder} can be used in order to construct NPC dialogs which
 * can be displayed in the client software. Usually it will be sued inside
 * scripts.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DialogBuilder {

	private final List<DialogNode> nodes = new ArrayList<>();
	
	public DialogBuilder() {
		// no op.
	}

	public void text(String txt) {
		final DialogNode n = new DialogNode(DialogType.TEXT, txt);
		nodes.add(n);
	}
	
	public void name(String name) {
		final DialogNode n = new DialogNode(DialogType.NAME, name);
		nodes.add(n);
	}

	/**
	 * Creates the {@link DialogMessage} for sending to the client based on the
	 * previously entered data. The internal state will be resetted upon calling
	 * this method.
	 * 
	 * @return The build {@link DialogMessage}.
	 */
	public DialogMessage build() {
		
		final DialogMessage msg = new DialogMessage(nodes);
		
		clear();

		return msg;
	}

	/**
	 * Resets the internal state of the builder.
	 */
	public void clear() {
		nodes.clear();
	}

}
