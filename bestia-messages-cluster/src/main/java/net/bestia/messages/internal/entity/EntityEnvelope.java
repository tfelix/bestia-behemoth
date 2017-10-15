package net.bestia.messages.internal.entity;

import java.util.Objects;

import net.bestia.messages.EntityInternalMessage;

/**
 * Messages packed into this envelope is directly send to the sharded entity
 * actor which is managing an entity.
 * 
 * @author Thomas Felix
 *
 */
public class EntityEnvelope extends EntityInternalMessage {
	
	private static final long serialVersionUID = 1L;
	private final Object payload;

	public EntityEnvelope(long entityId, Object payload) {
		super(entityId);
		
		this.payload = Objects.requireNonNull(payload);
	}
	
	public Object getPayload() {
		return payload;
	}
}
