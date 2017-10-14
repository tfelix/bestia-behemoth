package net.bestia.messages.cluster.entity;

import java.util.Objects;

import net.bestia.messages.EntityInternalMessage;

/**
 * This class wraps a message directed towards a component of an entity. This
 * wrapper can be safely send towards the entity manager actor which will lookup
 * the entity send it to its actor which in turn looks up the component actor
 * and deliver the message to them.
 * 
 * @author Thomas Felix
 *
 */
public final class ComponentPayloadWrapper extends EntityInternalMessage {

	private static final long serialVersionUID = 1L;
	private final Object payload;
	private final long componentId;

	public ComponentPayloadWrapper(long entityId, long componentId, Object payload) {
		super(entityId);

		this.componentId = componentId;
		this.payload = Objects.requireNonNull(payload);
	}

	public Object getPayload() {
		return payload;
	}

	public long getComponentId() {
		return componentId;
	}

	@Override
	public String toString() {
		return String.format("ComponentPayloadWrapper[eid: %d, compId: %d, payload: %s...]", getEntityId(), componentId,
				payload.toString().substring(0, 10));
	}
}