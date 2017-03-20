package net.bestia.messages.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * Used if the spawn of a new entity was signaled.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySpawnMessage extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;

	public EntitySpawnMessage(long entityId) {
		super(entityId);
	}

	@Override
	public String toString() {
		return String.format("EntitySpawnMessage[eeid: %d]", getEntityId());
	}
}
