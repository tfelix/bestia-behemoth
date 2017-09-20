package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.EntityJsonMessage;

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 * 
 * @author Thomas Felix
 *
 */
public class EntityInteractionRequestMessage extends EntityJsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.interactreq";
	
	@JsonProperty("ieid")
	private long interactedEntityId;

	/**
	 * Priv ctor for jackson.
	 */
	protected EntityInteractionRequestMessage() {
		// no op.
	}

	public EntityInteractionRequestMessage(long accId, long entityId, long interactEntityId) {
		super(accId, entityId);
		
		this.interactedEntityId = interactEntityId;
	}
	
	public long getInteractedEntityId() {
		return interactedEntityId;
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
		return new EntityInteractionRequestMessage(accountId, getEntityId(), getInteractedEntityId());
	}
}
