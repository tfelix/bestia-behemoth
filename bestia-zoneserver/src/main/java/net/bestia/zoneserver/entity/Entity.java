package net.bestia.zoneserver.entity;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.component.Component;

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

	private final Map<String, Long> components = new HashMap<>();

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
		final String simpleName = comp.getClass().getSimpleName();
		LOG.trace("Adding component {} (id: {}) to entity id: {}.", simpleName, comp.getId(), getId());
		components.put(simpleName, comp.getId());
	}

	void removeComponent(Component comp) {
		final String simpleName = comp.getClass().getSimpleName();
		LOG.trace("Removing component {} from entity id: {}.", simpleName, getId());
		components.remove(simpleName);
	}

	/**
	 * Removes the component via its id from the entity.
	 * 
	 * @param compId
	 *            The ID of the component to be removed.
	 */
	void removeComponent(Long compId) {
		LOG.trace("Removing component id {} from entity: {}.", compId, getId());
		components.entrySet()
				.stream()
				.filter(entry -> Objects.equals(entry.getValue(), compId))
				.map(Map.Entry::getKey)
				.findFirst()
				.ifPresent(components::remove);
	}

	long getComponentId(Class<? extends Component> clazz) {

		if (!components.containsKey(clazz.getSimpleName())) {
			return 0;
		} else {
			return components.get(clazz.getSimpleName());
		}
	}

	/**
	 * @return Return all assigned component ids.
	 */
	Collection<Long> getComponentIds() {
		return components.values();
	}

	@Override
	public String toString() {
		return String.format("Entity[id: %d]", getId());
	}

	@Override
	public boolean equals(Object o) {
		if (o == this)
			return true;
		if (!(o instanceof Entity)) {
			return false;
		}

		Entity entity = (Entity) o;
		return id == entity.id &&
				Objects.equals(components, entity.components);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, components);
	}
}
