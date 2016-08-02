package net.bestia.messages.bestia;

import net.bestia.messages.Message;

/**
 * Client sends this message if it wants to switch to another active bestia.
 * This bestia from now on is responsible for gathering all visual information.
 * And the client will get updated about these data.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class BestiaActivateMessage extends Message {

	private static final long serialVersionUID = 1L;
	public final static String MESSAGE_ID = "bestia.activate";

	/**
	 * Ctor.
	 */
	public BestiaActivateMessage() {
		
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
