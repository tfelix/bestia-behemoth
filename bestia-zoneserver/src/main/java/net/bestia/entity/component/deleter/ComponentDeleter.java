package net.bestia.entity.component.deleter;

import java.util.Objects;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

/**
 * The {@link ComponentDeleter} takes care of de-allocating all used up
 * resources by a component.
 * 
 * @author Thomas Felix
 *
 */
public abstract class ComponentDeleter<T extends Component> {

	private final Class<T> type;
	protected final EntityService entityService;

	protected ComponentDeleter(EntityService entityService, Class<T> type) {

		this.type = Objects.requireNonNull(type);
		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * The type of the component which is deleted by this deleter.
	 * 
	 * @return The supported component type.
	 */
	public Class<T> supportedComponent() {
		return type;
	}

	/**
	 * Frees a component from the entity.
	 * 
	 * @param component
	 *            The component to be removed.
	 */
	public void freeComponent(Component component) {
		if (component.getClass().isAssignableFrom(type)) {
			doFreeComponent(type.cast(component));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this recycler.");
		}
	}

	/**
	 * Performs the removal of this component from the system.
	 * 
	 * @param component
	 *            The deleted component.
	 */
	protected abstract void doFreeComponent(T component);
}
