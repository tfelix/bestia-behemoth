package net.bestia.messages.internal.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * Message controls if the active regen tick actor should be active. This is
 * important for actors which have status values.
 * 
 * @author Thomas Felix
 *
 */
public class EntityRegenTickMessage extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;
	private final boolean isActive;

	public EntityRegenTickMessage(long entityId, boolean isActive) {
		super(entityId);

		this.isActive = isActive;
	}

	public boolean isActive() {
		return isActive;
	}
}
