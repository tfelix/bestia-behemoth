package bestia.entity.component.interceptor;

import java.util.Objects;

import bestia.entity.Entity;
import bestia.entity.EntityService;
import bestia.entity.component.Component;

/**
 * This class is responsible to watch for certain components when they get
 * persisted back to the database. Some of the components have visual effects or
 * side effects which must be communicated to the player clients in range
 * immediately. This class takes care of generating messages and sending them or
 * taking other actions based upon the new component values.
 * 
 * @author Thomas Felix
 *
 */
public abstract class BaseComponentInterceptor<T extends Component> {

	private final Class<T> triggerClass;

	BaseComponentInterceptor(Class<T> type) {
		this.triggerClass = Objects.requireNonNull(type);
	}

	/**
	 * @return The class which triggers this intercepter.
	 */
	public Class<T> getTriggerType() {
		return triggerClass;
	}

	public void triggerCreateAction(EntityService entityService, Entity entity, Component comp) {
		if (comp.getClass().isAssignableFrom(triggerClass)) {
			onCreateAction(entityService, entity, triggerClass.cast(comp));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this interceptor.");
		}
	}

	public void triggerUpdateAction(EntityService entityService, Entity entity, Component comp) {
		if (comp.getClass().isAssignableFrom(triggerClass)) {
			onUpdateAction(entityService, entity, triggerClass.cast(comp));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this interceptor.");
		}
	}

	public void triggerDeleteAction(EntityService entityService, Entity entity, Component comp) {
		if (comp.getClass().isAssignableFrom(triggerClass)) {
			onDeleteAction(entityService, entity, triggerClass.cast(comp));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this interceptor.");
		}
	}

	/**
	 * This method is called if a trigger of the given component deletion is
	 * detected.
	 * 
	 * @param entityService
	 *            The entity service.
	 * @param entity
	 *            The entity which previously owned the deleted component.
	 * @param comp
	 *            The deleted component.
	 */
	protected abstract void onDeleteAction(EntityService entityService, Entity entity, T comp);

	/**
	 * This method is called if a trigger of the given component is detected.
	 * 
	 * @param entity
	 *            The entity to which the component belongs.
	 * @param comp
	 *            The component which is persisted to the database.
	 */
	protected abstract void onUpdateAction(EntityService entityService, Entity entity, T comp);

	/**
	 * This method is called when the component was created and is attached to
	 * an entity.
	 * 
	 * @param entityService
	 * @param entity
	 *            The entity to which the component belongs.
	 * @param comp
	 *            The component which was created.
	 */
	protected abstract void onCreateAction(EntityService entityService, Entity entity, T comp);
}
