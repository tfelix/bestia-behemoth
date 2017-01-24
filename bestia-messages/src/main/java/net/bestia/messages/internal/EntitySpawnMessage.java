package net.bestia.messages.internal;

import net.bestia.messages.Message;

/**
 * Used if the spawn of a new entity was signaled.
 * 
 * @author Thomas Felix
 *
 */
public class EntitySpawnMessage extends Message {

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
	public long getEntityId() {
		return entityId;
	}
}
