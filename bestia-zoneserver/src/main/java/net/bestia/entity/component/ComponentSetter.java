package net.bestia.entity.component;

import net.bestia.zoneserver.entity.component.Component;

import java.util.Objects;

/**
 * The component setter are an automated system to initially set the data of a
 * newly created entity. For all components which should be set there should be
 * an instance of a component setter performing this task.
 * 
 * @author Thomas Felix
 *
 * @param <T>
 *            The component which should be set by the implementation of this
 *            {@link ComponentSetter}.
 */
public abstract class ComponentSetter<T extends Component> {

	private final Class<T> type;

	ComponentSetter(Class<T> type) {
		this.type = Objects.requireNonNull(type);
	}

	/**
	 * @return The supported class type of this setter.
	 */
	public Class<T> getSupportedType() {
		return type;
	}

	/**
	 * Small helper method to transform the incoming type. If this is the wrong
	 * component type. Do nothing.
	 * 
	 * @param component
	 *            The component to be set via this setter.
	 */
	public void setComponent(Component component) {
		Objects.requireNonNull(component);

		// Can I cast?
		if (!type.isAssignableFrom(component.getClass())) {
			return;
		}

		performSetting(type.cast(component));
	}

	/**
	 * Performs the actual setting of the component. Used for convenience so we
	 * are presented the correct component type to begin with setting the
	 * variables.
	 * 
	 * @param comp
	 *            The component to be set.
	 */
	protected abstract void performSetting(T comp);
}
