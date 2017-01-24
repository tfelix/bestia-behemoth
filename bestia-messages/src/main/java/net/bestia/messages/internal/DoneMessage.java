package net.bestia.messages.internal;

/**
 * Simple message which can be used to signal that an event has been done. It
 * carries a simple tag to identify which origin the message had.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class DoneMessage extends InternalMessage {

	private static final long serialVersionUID = 1L;
	private final String tag;

	public DoneMessage() {
		tag = "";
	}

	public DoneMessage(String tag) {
		this.tag = tag;
	}

	public String getTag() {
		return tag;
	}
}
