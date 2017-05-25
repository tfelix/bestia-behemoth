package net.bestia.zoneserver.entity.component;

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
	 * Small helper method to transform the incoming type
	 * 
	 * @param addedComp
	 */
	public void setComponent(Component addedComp) {

		// Can I cast?
		if (!type.isAssignableFrom(addedComp.getClass())) {
			return;
		}

		performSetting(type.cast(addedComp));
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
