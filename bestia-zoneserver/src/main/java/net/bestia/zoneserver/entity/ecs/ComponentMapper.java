package net.bestia.zoneserver.entity.ecs;

import java.util.Optional;

public class ComponentMapper<T extends Component> {
	
	private final ComponentType componentType;
	
	private ComponentMapper (Class<T> componentClass) {
		componentType = ComponentType.getFor(componentClass);
	}

	public static <T extends Component> ComponentMapper<T> getFor (Class<T> componentClass) {
		return new ComponentMapper<T>(componentClass);
	}
	
	/** @return The {@link Component} of the specified class belonging to entity. */
	public T get (Entity entity) {
		return entity.getComponent(componentType);
	}

}
