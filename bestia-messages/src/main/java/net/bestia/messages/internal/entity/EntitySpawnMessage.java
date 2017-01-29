package net.bestia.messages.internal.entity;

import net.bestia.messages.EntityMessage;
import net.bestia.messages.Message;

/**
 * Used if the spawn of a new entity was signaled.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySpawnMessage extends Message implements EntityMessage {

	private static final long serialVersionUID = 1L;

	private final long entityId;

	public EntitySpawnMessage(long entityId) {

		this.entityId = entityId;
	}

	/**
	 * The entity id which was spawned and already created for the cache.
	 * 
	 * @return
	 */
	@Override
	public long getEntityId() {
		return entityId;
	}
}
