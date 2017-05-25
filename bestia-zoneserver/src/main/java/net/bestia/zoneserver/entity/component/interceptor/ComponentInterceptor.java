package net.bestia.zoneserver.entity.component.interceptor;

import java.util.Objects;

import net.bestia.zoneserver.entity.Entity;
import net.bestia.zoneserver.entity.EntityService;
import net.bestia.zoneserver.entity.component.Component;

/**
 * This class is responsible to watch for certain components when they get
 * persistet back to the database. Some of the components have visual effects or
 * side effects which must be communicated to the player clients in range
 * immediately. This class takes care of generating messages and sending them or
 * taking other actions based upon the new component values.
 * 
 * @author Thomas
 *
 */
public abstract class ComponentInterceptor<T extends Component> {

	private final Class<T> triggerClass;

	ComponentInterceptor(Class<T> type) {
		this.triggerClass = Objects.requireNonNull(type);
	}

	/**
	 * @return The class which triggers this interceptor.
	 */
	public Class<T> getTriggerType() {
		return triggerClass;
	}

	/**
	 * This method is called if a trigger of the given component is detected.
	 * 
	 * @param comp
	 *            The component which is persisted to the database.
	 */
	public abstract void triggerUpdateAction(EntityService entityService, Entity entity, T comp);
	
	public abstract void triggerDeleteAction(EntityService entityService, Entity entity, T comp);
	
	public abstract void triggerCreateAction(EntityService entityService, Entity entity, T comp);
}
