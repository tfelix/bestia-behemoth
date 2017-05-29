package net.bestia.zoneserver.entity;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;

import net.bestia.zoneserver.entity.component.Component;

/**
 * The {@link EntityRecycler} is used to get rid of an entity and its
 * components. It is also used to avoid reinstancing entity objects. It can be
 * asked to fetch objects of a certain size again.
 * 
 * @author Thomas Felix
 *
 */
public class EntityRecycler {

	private static final int DEFAULT_MAX_CACHED_INSTANCES = 1000;

	private final int maxCachedInstances;

	private final Queue<Entity> entities = new LinkedList<>();
	private final Map<Class<? extends Component>, Queue<? extends Component>> components = new HashMap<>();
	private final EntityServiceContext entityServiceCtx;

	public EntityRecycler(int maxCachedInstances, EntityServiceContext entityServiceCtx) {
		if (maxCachedInstances < 0) {
			throw new IllegalArgumentException("MaxCachedInstances must be positive.");
		}

		this.maxCachedInstances = maxCachedInstances;
		this.entityServiceCtx = Objects.requireNonNull(entityServiceCtx);
	}

	public EntityRecycler(EntityServiceContext entityServiceCtx) {
		this(DEFAULT_MAX_CACHED_INSTANCES, entityServiceCtx);
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
		
		//entityServiceCtx.getEntity().ge
	}
	
	public void free(long entityId) {
		final Entity e = entityServiceCtx.getEntity().getEntity(entityId);
		
		if(e == null) {
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

		if (components.containsKey(componentClass)) {
			return (T) components.get(componentClass).poll();
		} else {
			return null;
		}

	}
}
