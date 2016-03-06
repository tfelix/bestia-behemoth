package net.bestia.messages.bestia;

import net.bestia.messages.InputMessage;

/**
 * Message returned to the client if it should set a selected bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivatedMessage extends InputMessage {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activated";

	/**
	 * Ctor.
	 */
	public BestiaActivatedMessage() {

	}

	public BestiaActivatedMessage(BestiaActivateMessage msg) {
		super(msg.getAccountId(), msg.getPlayerBestiaId());
		// no op.
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
