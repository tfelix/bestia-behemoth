package net.bestia.messages.entity;

import com.fasterxml.jackson.annotation.JsonProperty;

import net.bestia.messages.JsonMessage;

/**
 * By sending this message the client wants to get to know how he is able to
 * interact with the given entity. The server will respond with a list of
 * possible interactions.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public class EntityInteractionRequestMessage extends JsonMessage {

	private static final long serialVersionUID = 1L;

	public static final String MESSAGE_ID = "entity.interactreq";
	
	@JsonProperty("eid")
	private final long entityId;
	
	public EntityInteractionRequestMessage() {
		entityId = 0;
	}
	
	public EntityInteractionRequestMessage(long eid) {
		this.entityId = eid;
	}
	
	public long getEntityId() {
		return entityId;
	}
	
	@Override
	public String toString() {
		return String.format("EntityInteractionReqMsg[eid: %d]", getEntityId());
	}

	@Override
	public String getMessageId() {
		return MESSAGE_ID;
	}
}
