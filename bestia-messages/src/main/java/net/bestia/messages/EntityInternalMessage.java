package net.bestia.messages;

import java.io.Serializable;

/**
 * This is a message to be used only internally between the bestia server to
 * send updates regarding to entities. This message holds only an entity id. All
 * messages concerning entities internally should extend this message.
 * 
 * @author Thomas Felix
 *
 */
public abstract class EntityInternalMessage extends Message implements EntityMessage, Serializable {

	private static final long serialVersionUID = 1L;
	
	private final long entityId;
	
	public EntityInternalMessage(long entityId) {
		
		this.entityId = entityId;
	}
	
	@Override
	public String toString() {
		return String.format("EntityInternalMessage[eeid: %d]", getEntityId());
	}

	@Override
	public long getEntityId() {
		return entityId;
	}

}
