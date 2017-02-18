package net.bestia.messages.entity;

import net.bestia.messages.EntityJsonMessage;

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityInteractionRequestMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.interactreq";

	/**
	 * Priv ctor for jackson.
	 */
	protected EntityInteractionRequestMessage() {
		// no op.
	}

	public EntityInteractionRequestMessage(long accId, long eid) {
		super(accId, eid);
	}

	@Override
	public String toString() {
		return String.format("EntityInteractionReqMsg[accId: %d, eid: %d]", getAccountId(), getEntityId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}

	@Override
	public EntityInteractionRequestMessage createNewInstance(long accountId) {
		return new EntityInteractionRequestMessage(accountId, getEntityId());
	}
}
