package net.bestia.messages.entity;

import net.bestia.messages.Message;

/**
 * By sending this message to the server the client indicates that it want to
 * interact with the given entity. The server is responsible for looking up the
 * entity and invoking the given handler for handling the request. Additional
 * data might get send by this request.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityInteractionRequestMessage extends Message {
	
	private static final long serialVersionUID = 1L;
	
	public static final String MESSAGE_ID = "entity.interactreq";
	
	

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public String getMessagePath() {
		return getZoneMessagePath();
	}

	
}
