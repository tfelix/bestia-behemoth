package net.bestia.zoneserver.script;

import java.util.ArrayList;
import java.util.List;

import net.bestia.messages.ui.DialogAction;
import net.bestia.messages.ui.DialogMessage;
import net.bestia.messages.ui.DialogNode;

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
	private final long receiverAccId;

	public DialogBuilder(long receiverAccId) {
		
		this.receiverAccId = receiverAccId;
	}

	public void text(String txt) {
		final DialogNode n = new DialogNode(DialogAction.TEXT, txt);
		nodes.add(n);
	}

	public void name(String name) {
		final DialogNode n = new DialogNode(DialogAction.NAME, name);
		nodes.add(n);
	}

	/**
	 * Closes the dialog with the given message.
	 * 
	 * @param dialogId
	 *            ID of the dialog to be closed.
	 * @return Message for closing the dialog.
	 */
	public DialogMessage close(String dialogId) {
		nodes.add(new DialogNode(DialogAction.CLOSE, dialogId));
		final DialogMessage msg = new DialogMessage(receiverAccId, nodes);

		clear();

		return msg;
	}

	/**
	 * Creates the {@link DialogMessage} for sending to the client based on the
	 * previously entered data. The internal state will be resetted upon calling
	 * this method.
	 * 
	 * @param dialogId
	 *            The ID of the dialog. Each dialog must be identified by an
	 *            unique id.
	 * @return The build {@link DialogMessage}.
	 */
	public DialogMessage build(String dialogId) {
		final DialogMessage msg = new DialogMessage(receiverAccId, nodes);

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
