package net.bestia.zoneserver.entity.ecs;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.util.PackageLoader;

/**
 * Entities can be attached with components in order to make a composition of a
 * complex entity which can be interacted with inside the bestia system.
 * 
 * @author Thomas Felix
 *
 */
public class Entity implements Serializable {

	private static final Logger LOG = LoggerFactory.getLogger(Entity.class);

	private static final long serialVersionUID = 1L;
	private static final int MAX_COMPONENT_ID;
	static {
		// we need to fing the max component id.
		PackageLoader<Component> loader = new PackageLoader<>(Component.class, "net.bestia.zoneserver.entity.ecs");

		int maxId = 0;
		for (Class<? extends Component> clazz : loader.getSubClasses()) {
			try {
				Method idMethod = clazz.getDeclaredMethod("getComponentId");
				Integer id = (Integer) idMethod.invoke(null);

				if (id > maxId) {
					maxId = id;
				}
			} catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				LOG.error("Could not get the ID of the component.", e);
			}
		}

		MAX_COMPONENT_ID = maxId;
	}

	private long id = -1;
	
	private Component[] components = new Component[MAX_COMPONENT_ID];
	private Set<Component> components2 = new HashSet<>();

	/**
	 * This will set an id of -1.
	 */
	Entity(long id) {
		this.id = id;
	}

	public long getId() {
		return id;
	}
	
	public void addComponent(Component comp) {
		components2.add(comp);
	}

	public <T extends Component> T getComponent(int compId, Class<T> type) {
		return type.cast(components[compId]);
	}
	
	public <T extends Component> T getComponent(Class<T> type) {
		return null;
	}

	/**
	 * Internal use.
	 * @return The {@link Component} object for the specified class, null if the Entity does not have any components for that class.
	 */
	@SuppressWarnings("unchecked")
	<T extends Component> T getComponent (ComponentType componentType) {
		int componentTypeIndex = componentType.getIndex();

		if (componentTypeIndex < components.getCapacity()) {
			return (T)components.get(componentType.getIndex());
		} else {
			return null;
		}
	}
}
