package net.bestia.messages.internal.entity;

import java.util.Objects;

/**
 * This class wraps a message directed towards a component of an entity. This
 * wrapper can be safely send towards the entity manager actor which will lookup
 * the entity send it to its actor which in turn looks up the component actor
 * and deliver the message to them.
 * 
 * @author Thomas Felix
 *
 */
public final class ComponentPayloadWrapper {

	private final Object payload;
	
	private final long componentId;
	private final long entityId;

	public ComponentPayloadWrapper(long entityId, long componentId, Object payload) {

		this.entityId = entityId;
		this.componentId = componentId;
		this.payload = Objects.requireNonNull(payload);
	}

	public Object getPayload() {
		return payload;
	}

	public long getComponentId() {
		return componentId;
	}
	
	public long getEntityId() {
		return entityId;
	}
}
