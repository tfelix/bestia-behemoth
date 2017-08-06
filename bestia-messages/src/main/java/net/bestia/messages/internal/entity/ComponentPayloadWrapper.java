package net.bestia.messages.internal.entity;

import java.util.Objects;

/**
 * This class wraps a message directed towards a component of an entity. This
 * wrapper can be safely send towards and entity actor which will lookup the
 * associated component actor and deliver the message to them.
 * 
 * @author Thomas Felix
 *
 */
public final class ComponentPayloadWrapper {

	private final Object payload;
	private final long componentId;

	public ComponentPayloadWrapper(long componentId, EntityMoveInternalMessage payload) {

		this.componentId = componentId;
		this.payload = Objects.requireNonNull(payload);
	}

	public Object getPayload() {
		return payload;
	}

	public long getComponentId() {
		return componentId;
	}
}
