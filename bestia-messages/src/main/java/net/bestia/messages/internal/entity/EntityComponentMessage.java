package net.bestia.messages.internal.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * This message is send to an entity actor to signal that a new actor should be
 * spawned who handles a special
 * 
 * @author Thomas Felix
 *
 */
public class EntityComponentMessage extends EntityInternalMessage {

	public enum ComponentState {
		INSTALL, REMOVE
	}

	private static final long serialVersionUID = 1L;

	private final long componentId;
	private final ComponentState state;

	public EntityComponentMessage(long entityId, long componentId, ComponentState state) {
		super(entityId);
		
		this.componentId = componentId;
		this.state = state;
	}

	public long getComponentId() {
		return componentId;
	}
	
	public ComponentState getState() {
		return state;
	}
}
