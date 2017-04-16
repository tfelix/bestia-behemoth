package net.bestia.zoneserver.entity.ecs;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.zoneserver.entity.ecs.components.Component;

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

	public long getId() {
		return id;
	}
	
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
	
	long getComponentId(Class<Component> clazz) {
		
		if(!components.containsKey(clazz.getName())) {
			return 0;
		} else {
			return components.get(clazz.getName());
		}
	}
}
