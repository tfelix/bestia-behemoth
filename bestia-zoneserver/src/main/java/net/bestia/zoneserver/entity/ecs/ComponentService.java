package net.bestia.zoneserver.entity.ecs;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Entities can be very short lived in order to save performance references to
 * entity components are kept and can later be reused.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class ComponentService {
	
	private final static Logger LOG = LoggerFactory.getLogger(ComponentService.class);

	/**
	 * Number of component instances per type kept in the pool.
	 */
	private static int MAX_COMPONENT_TYPE_POOL = 100;
	private Map<String, Queue<Component>> componentPool = new HashMap<>();

	public Component getComponentInstance(Class<Component> compClass) {
		
		// Currently we dont pool, we only create new instances.
		try {
			Component comp = compClass.getConstructor().newInstance();
			return comp;
		} catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			
			// This should not happen.
			LOG.error("Can not instanciate component.", e);
			return null;
		}

	}

	/**
	 * Returns a now not used component back to the pool.
	 * 
	 * @param compClass
	 */
	public void freeComponent(Component unused) {
		LOG.trace("Returned component instance to pool: {}", unused);
		// TODO implementieren.
	}
	
	public void addComponent(long entityId, Component comp) {
		throw new IllegalStateException();
	}

	public void removeComponent(long entityId, Component comp) {
		throw new IllegalStateException();
	}

	public Collection<Entity> getEntitiesWithComponent(Class<Component>... compClass) {
		throw new IllegalStateException();
	}

	/**
	 * 
	 * @param e
	 * @param comp
	 * @return
	 */
	/*
	public boolean hasComponent(Entity e, Class<? extends Component> comp) {

		return entityComponents.get(e.getId())
				.stream()
				.filter(c -> comp.isInstance(c))
				.findFirst()
				.map(x -> true)
				.orElse(false);
	}*/

}
