package net.bestia.entity.recycler;

import java.util.Objects;

import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

/**
 * The {@link ComponentCleaner} takes care of de-allocating all used up
 * resources by a component.
 * 
 * @author Thomas Felix
 *
 */
public abstract class ComponentCleaner<T extends Component> {

	private final Class<T> type;
	protected final EntityService entityService;

	protected ComponentCleaner(EntityService entityService, Class<T> type) {

		this.type = Objects.requireNonNull(type);
		this.entityService = Objects.requireNonNull(entityService);
	}

	public Class<T> supportedComponent() {
		return type;
	}

	public void freeComponent(Component component) {
		if (component.getClass().isAssignableFrom(type)) {
			freeComponent(type.cast(component));
		} else {
			throw new IllegalArgumentException("Wrong component class. Not supported by this recycler.");
		}
	}

	protected abstract void doFreeComponent(T component);
}
