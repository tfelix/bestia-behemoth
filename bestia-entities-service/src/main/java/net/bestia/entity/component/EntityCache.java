package net.bestia.entity.component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.bestia.entity.Entity;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.interceptor.BaseComponentInterceptor;

/**
 * The {@link EntityCache} is used to get rid of an entity and its
 * components. It is also used to avoid reinstancing entity objects. It can be
 * asked to fetch objects of a certain size again.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class EntityCache {

	private static final Logger LOG = LoggerFactory.getLogger(EntityCache.class);
	private static final int DEFAULT_MAX_CACHED_INSTANCES = 1000;

	private final int maxCachedInstances;

	private final Queue<Entity> entities = new LinkedList<>();
	private final Map<String, Queue<Component>> components = new HashMap<>();

	public EntityCache(int maxCachedInstances,
			List<BaseComponentInterceptor<? extends Component>> interceptors) {

		if (maxCachedInstances < 0) {
			throw new IllegalArgumentException("MaxCachedInstances must be positive.");
		}

		this.maxCachedInstances = maxCachedInstances;
	}

	@Autowired
	public EntityCache(List<BaseComponentInterceptor<? extends Component>> interceptors) {
		this(DEFAULT_MAX_CACHED_INSTANCES, interceptors);
	}


	/**
	 * Saves the component for later reuse. Null components can be given since
	 * we check here in a central point and just ignore them.
	 * 
	 * @param component
	 *            The component to stash away for later reuse. Can be null the
	 *            nothing happens.
	 */
	public void stashComponente(Component component) {
		if (component == null) {
			return;
		}

		LOG.trace("Stashing component: {}", component);

		component.setEntityId(0);

		final Class<? extends Component> compClass = component.getClass();

		if (!components.containsKey(compClass)) {
			components.put(compClass.getName(), new LinkedList<>());
		}

		final Queue<Component> queue = components.get(compClass.getName());

		if (queue.size() >= maxCachedInstances) {
			return;
		}

		queue.offer(component);
	}

	/**
	 * Stash away an entity for later use.
	 * 
	 */
	public void stashEntity(Entity entity) {
		LOG.trace("Stashing entity: {}", entity);

		if (entities.size() >= maxCachedInstances) {
			return;
		}

		entities.offer(entity);
	}

	/**
	 * Returns an entity which was previously cached. It does not contain
	 * components anymore. It returns null if there is no entity available.
	 * 
	 * @return A cached entity or null if no entity is available.
	 */
	public Entity getEntity() {
		return entities.poll();
	}

	/**
	 * Returns a previously cached component without instancing it as a new one.
	 * 
	 * @param componentClass
	 *            The class of the component to retrieve.
	 * @return A instance of a component of null of no component of this class
	 *         is currently available.
	 */
	@SuppressWarnings("unchecked")
	public <T extends Component> T getComponent(Class<T> componentClass) {

		final String className = componentClass.getName();

		if (components.containsKey(className)) {
			return (T) components.get(className).poll();
		} else {
			return null;
		}

	}
}
