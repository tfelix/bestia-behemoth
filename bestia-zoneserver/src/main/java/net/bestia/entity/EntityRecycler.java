package net.bestia.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.bestia.entity.component.Component;
import net.bestia.entity.component.ScriptComponent;
import net.bestia.zoneserver.script.ScriptService;

/**
 * The {@link EntityRecycler} is used to get rid of an entity and its
 * components. It is also used to avoid reinstancing entity objects. It can be
 * asked to fetch objects of a certain size again.
 * 
 * @author Thomas Felix
 *
 */
public class EntityRecycler {

	private static final Logger LOG = LoggerFactory.getLogger(EntityRecycler.class);
	private static final int DEFAULT_MAX_CACHED_INSTANCES = 1000;

	private final int maxCachedInstances;

	private final Queue<Entity> entities = new LinkedList<>();
	private final Map<String, Queue<Component>> components = new HashMap<>();
	
	private final EntityService entityService;
	private final ScriptService scriptService;

	public EntityRecycler(int maxCachedInstances, EntityService entityService, ScriptService scriptService) {
		if (maxCachedInstances < 0) {
			throw new IllegalArgumentException("MaxCachedInstances must be positive.");
		}

		this.maxCachedInstances = maxCachedInstances;
		this.entityService = Objects.requireNonNull(entityService);
		this.scriptService = Objects.requireNonNull(scriptService);
	}

	public EntityRecycler(EntityService entityService, ScriptService scriptService) {
		this(DEFAULT_MAX_CACHED_INSTANCES, entityService, scriptService);
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

		// Remove/Recycle the special handled components.
		freeSpecialComponents(entity);

		final Collection<Component> components = entityService.getAllComponents(entity);
		entityService.deleteAllComponents(entity);
		components.stream().forEach(this::stashComponente);

		// Save entity.
		entityService.delete(entity);
		stashEntity(entity);
	}

	/**
	 * Removes all the special handles components from an entity.
	 */
	private void freeSpecialComponents(Entity entity) {

		final ScriptComponent scriptComp = scriptService.deleteScriptComponent(entity);
		stashComponente(scriptComp);
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
