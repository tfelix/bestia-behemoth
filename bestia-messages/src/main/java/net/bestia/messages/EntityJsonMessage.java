package net.bestia.messages;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Class can be send to a client and contains an entity id.
 * 
 * @author Thomas Felix
 *
 */
public abstract class EntityJsonMessage extends JsonMessage implements EntityMessage {

	private static final long serialVersionUID = 1L;

	@JsonProperty("eid")
	private long entityId;
	
	public EntityJsonMessage(long accId, long entityId) {
		super(accId);
		
		if (entityId < 0) {
			throw new IllegalArgumentException("EntityID must be positive or 0.");
		}

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
}
