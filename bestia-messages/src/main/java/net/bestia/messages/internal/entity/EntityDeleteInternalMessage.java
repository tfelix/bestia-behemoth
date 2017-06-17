package net.bestia.messages.internal.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * Message send to the bestia system to delete a specific entity.
 * 
 * @author Thomas Felix
 *
 */
public class EntityDeleteInternalMessage extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;

	private final long delayMs;

	/**
	 * Using this ctor the entity is immediatly deleted without delay.
	 * 
	 * @param entityId
	 *            The entity id to delete/remove from the system.
	 */
	public EntityDeleteInternalMessage(long entityId) {
		this(entityId, 0);
	}
	
	public EntityDeleteInternalMessage(long entityId, long delayMs) {
		super(entityId);
		
		if(delayMs < 0) {
			throw new IllegalArgumentException("Delay can not be negative.");
		}

		this.delayMs = delayMs;
	}

	/**
	 * Returns the delay until a entity should be deleted from the system.
	 * 
	 * @return The delay in ms until the entity should be deleted.
	 */
	public long getDelayMs() {
		return delayMs;
	}

	@Override
	public String toString() {
		return String.format("EntityDeleteInternalMessage[eeid: %d]", getEntityId());
	}

}
