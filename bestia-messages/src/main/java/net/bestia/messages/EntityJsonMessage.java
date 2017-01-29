package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class can be send to a client and contains an entity id.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
public abstract class EntityJsonMessage extends JsonMessage implements EntityMessage {

	private static final long serialVersionUID = 1L;

	@JsonProperty("eid")
	private long entityId;
	
	public EntityJsonMessage() {
		// no op.
	}

	public EntityJsonMessage(long accId, long entityId) {
		super(accId);
		
		this.entityId = entityId;
	}

	@Override
	public String toString() {
		return String.format("EntityJsonMessage[eeid: %d, accId: %s]", getEntityId(), getAccountId());
	}

	@Override
	public long getEntityId() {
		return entityId;
	}
	
	public void setEntityId(long entityId) {
		this.entityId = entityId;
	}

}
