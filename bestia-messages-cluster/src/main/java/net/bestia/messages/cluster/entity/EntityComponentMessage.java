package net.bestia.messages.cluster.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * This message is send to an entity actor to signal that a new component actor
 * should be spawned who handles this component in any way. Usually this is the
 * case if there is some kind of periodic handling of some components like
 * script callbacks.
 * 
 * @author Thomas Felix
 *
 */
public class EntityComponentMessage extends EntityInternalMessage {

	public enum ComponentState {
		/**
		 * Signals the entity actor to start the component watching.
		 */
		INSTALL,

		/**
		 * This will remove the component observing.
		 */
		REMOVE
	}

	private static final long serialVersionUID = 1L;

	private final long componentId;
	private final ComponentState state;

	public EntityComponentMessage(long entityId, long componentId, ComponentState state) {
		super(entityId);

		this.componentId = componentId;
		this.state = state;
	}

	/**
	 * @return The component ID which should be processed.
	 */
	public long getComponentId() {
		return componentId;
	}

	/**
	 * @return The state if this actor should start or stop observing this
	 *         component.
	 */
	public ComponentState getState() {
		return state;
	}

	/**
	 * @param entityId
	 *            The entity id.
	 * @param componentId
	 *            The component ID to stop.
	 * @return Creates a message that will stop the given component actor.
	 */
	public static EntityComponentMessage stop(long entityId, long componentId) {
		return new EntityComponentMessage(entityId, componentId, ComponentState.REMOVE);
	}

	/**
	 * @param entityId
	 *            The entity id.
	 * @param componentId
	 *            The component ID to stop.
	 * @return Creates a message that will start the given component actor.
	 */
	public static EntityComponentMessage start(long entityId, long componentId) {
		return new EntityComponentMessage(entityId, componentId, ComponentState.INSTALL);
	}
}
