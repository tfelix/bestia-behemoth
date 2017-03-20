package net.bestia.messages.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * Message send to the bestia system to delete a specific entity.
 * 
 * @author Thomas Felix
 *
 */
public class EntityDeleteInternalMessage extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;

	/**
	 * Ctor.
	 * 
	 * @param entityId
	 *            The entity id to delete/remove from the system.
	 */
	public EntityDeleteInternalMessage(long entityId) {
		super(entityId);
		// no op.
	}

	@Override
	public String toString() {
		return String.format("EntityDeleteInternalMessage[eeid: %d]", getEntityId());
	}

}
