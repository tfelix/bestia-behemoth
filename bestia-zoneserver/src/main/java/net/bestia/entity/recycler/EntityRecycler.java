package net.bestia.entity.recycler;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.Component;

/**
 * The {@link EntityRecycler} is used to get rid of an entity and its
 * components. It is also used to avoid reinstancing entity objects. It can be
 * asked to fetch objects of a certain size again.
 * 
 * @author Thomas Felix
 *
 */
@org.springframework.stereotype.Component
public class EntityRecycler {

	private static final Logger LOG = LoggerFactory.getLogger(EntityRecycler.class);
	private static final int DEFAULT_MAX_CACHED_INSTANCES = 1000;

	private final int maxCachedInstances;

	private final Queue<Entity> entities = new LinkedList<>();
	private final Map<String, Queue<Component>> components = new HashMap<>();

	private final EntityService entityService;
	private final Map<Class<? extends Component>, ComponentRecycler<? extends Component>> recyclers = new HashMap<>();

	public EntityRecycler(int maxCachedInstances,
			EntityService entityService,
			List<ComponentRecycler<? extends Component>> componentRecyclers) {

		if (maxCachedInstances < 0) {
			throw new IllegalArgumentException("MaxCachedInstances must be positive.");
		}

		this.maxCachedInstances = maxCachedInstances;
		this.entityService = Objects.requireNonNull(entityService);

		for (ComponentRecycler<? extends Component> compRecycler : componentRecyclers) {
			recyclers.put(compRecycler.supportedComponent(), compRecycler);
		}
	}

	public EntityRecycler(EntityService entityService,
			List<ComponentRecycler<? extends Component>> componentRecyclers) {
		this(DEFAULT_MAX_CACHED_INSTANCES, entityService, componentRecyclers);
	}

	/**
	 * Frees the entity. It frees also its components and depending on the type
	 * of components it calls all the needed de-alloc calls from the certain
	 * services.
	 * 
	 * @param entity
	 *            The entity to delete.
	 */
	public void free(Entity entity) {
		Objects.requireNonNull(entity);

		LOG.debug("Recycling entity: {}", entity);

		final Collection<Component> components = entityService.getAllComponents(entity);
		entityService.deleteAllComponents(entity);

		components.stream().forEach(comp -> {
			freeComponent(entity.getId(), comp.getClass());
		});

		// Save entity.
		entityService.delete(entity);
		stashEntity(entity);
	}

	/**
	 * Alias to {@link #free(Entity)}.
	 * 
	 * @param entityId
	 *            The entity ID to free (and delete all the components).
	 */
	public void free(long entityId) {
		final Entity e = entityService.getEntity(entityId);

		if (e == null) {
			throw new IllegalArgumentException("Entity ID does not exist.");
		}

		free(e);
	}

	public void freeComponent(long entityId, Component comp) {

		// Check if we can recycle.
		if (!recyclers.containsKey(comp.getClass())) {
			LOG.warn("Can not recycle component: {}", comp.getClass());
			return;
		}

		final ComponentRecycler<? extends Component> recycler = recyclers.get(comp.getClass());
		recycler.freeComponent(comp);
		stashComponente(comp);
	}

	/**
	 * Frees a single component and cleans up all the associated resources with
	 * it. Alias to {@link #freeComponent(long, Component)}.
	 * 
	 * @param entityId
	 *            The entity to remove a component from.
	 * @param compClass
	 *            The class of the component to be removed.
	 */
	public void freeComponent(long entityId, Class<? extends Component> compClass) {

		final Optional<? extends Component> comp = entityService.getComponent(entityId, compClass);

		if (!comp.isPresent()) {
			return;
		} else {
			freeComponent(entityId, comp.get());
		}
	}

	/**
	 * Saves the component for later reuse. Null components can be given since
	 * we check here in a central point and just ignore them.
	 * 
	 * @param component
	 *            The component to stash away for later reuse. Can be null the
	 *            nothing happens.
	 */
	private void stashComponente(Component component) {
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
	private void stashEntity(Entity entity) {
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
