package net.bestia.messages.internal.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * When received by an entity actor it will kill itself.
 * 
 * @author Thomas Felix
 *
 */
public final class EntityKillMessage extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;

	public EntityKillMessage(long entityId) {
		super(entityId);
		// no op.
	}
}
