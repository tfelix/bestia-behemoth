package net.bestia.zoneserver.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.components.Component;

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

	private final long id;

	private Map<String, Long> components = new HashMap<>();

	Entity(long id) {
		this.id = id;
	}

	/**
	 * Returns the unique ID for each entity.
	 * 
	 * @return The unique ID for each entity.
	 */
	public long getId() {
		return id;
	}

	/**
	 * Adds a given component reference to this entity. Note that the component
	 * must be saved independently in the system, only the reference to it is
	 * stored inside the entity.
	 * 
	 * @param comp
	 *            The component to be added.
	 */
	void addComponent(Component comp) {
		final String simpleName = comp.getClass().getName();
		LOG.trace("Adding component {} to entity id: {}.", simpleName, getId());
		components.put(simpleName, comp.getId());
	}

	void removeComponent(Component comp) {
		final String simpleName = comp.getClass().getName();
		LOG.trace("Removing component {} to entity id: {}.", simpleName, getId());
		components.remove(simpleName);
	}

	long getComponentId(Class<? extends Component> clazz) {

		if (!components.containsKey(clazz.getName())) {
			return 0;
		} else {
			return components.get(clazz.getName());
		}
	}

	/**
	 * @return Return all assigned component ids.
	 */
	/*
	Collection<Long> getComponentIds() {
		return components.values();
	}*/
}
