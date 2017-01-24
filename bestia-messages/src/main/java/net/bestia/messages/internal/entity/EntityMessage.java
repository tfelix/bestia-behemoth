package net.bestia.messages.internal.entity;

import net.bestia.messages.internal.InternalMessage;

/**
 * Keeps an entity id.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
abstract class EntityMessage extends InternalMessage {
	
	private static final long serialVersionUID = 1L;
	
	private final long entityId;
	
	public EntityMessage(long entityId) {
		
		this.entityId = entityId;
	}
	
	public long getEntityId() {
		return entityId;
	}
}
