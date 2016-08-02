package net.bestia.messages.bestia;


import net.bestia.messages.Message;

/**
 * Message returned to the client if it should set a selected bestia.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivatedMessage extends Message {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activated";

	/**
	 * Ctor.
	 */
	public BestiaActivatedMessage() {

	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
