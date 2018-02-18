package net.bestia.messages.entity;

import net.bestia.messages.EntityInternalMessage;

/**
 * This message is send to an entity actor to signal that a new component actor
 * should be spawned who handles this component in any way. Usually this is the
 * case if there is some kind of periodic handling of some components like
 * script callbacks or the ticking effect of status components.
 * 
 * @author Thomas Felix
 *
 */
// TODO Dier hier in Install und Remove Message splitten. Dies vereinfacht code.
public class EntityComponentStateMessage extends EntityInternalMessage {

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

	public EntityComponentStateMessage(long entityId, long componentId, ComponentState state) {
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
	public static EntityComponentStateMessage remove(long entityId, long componentId) {
		return new EntityComponentStateMessage(entityId, componentId, ComponentState.REMOVE);
	}

	/**
	 * @param entityId
	 *            The entity id.
	 * @param componentId
	 *            The component ID to stop.
	 * @return Creates a message that will start the given component actor.
	 */
	public static EntityComponentStateMessage install(long entityId, long componentId) {
		return new EntityComponentStateMessage(entityId, componentId, ComponentState.INSTALL);
	}

	@Override
	public String toString() {
		return String.format("EntityComponentMessage[eid: %d, cid: %d, s: %s]", getEntityId(), getComponentId(),
				getState());
	}
}
