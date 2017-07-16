package net.bestia.entity;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;

import net.bestia.model.geometry.CollisionShape;
import net.bestia.entity.component.Component;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.interceptor.ComponentInterceptor;

@Service
public class EntityService {

	private static final Logger LOG = LoggerFactory.getLogger(EntityService.class);

	private final static String ECS_ENTITY_MAP = "entities";
	private final static String ENTITIES_ID_GEN = "entities.id";
	private static final String COMP_MAP = "components";
	private static final String COMP_ID_GEN = "components.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator entityIdGen;
	private final IMap<Long, Component> components;
	private final IdGenerator idGenerator;

	private final Map<Class<? extends Component>, List<ComponentInterceptor<? extends Component>>> interceptors = new HashMap<>();

	@Autowired
	public EntityService(HazelcastInstance hz) {

		Objects.requireNonNull(hz);

		this.entityIdGen = hz.getIdGenerator(ENTITIES_ID_GEN);
		this.entities = hz.getMap(ECS_ENTITY_MAP);
		this.idGenerator = hz.getIdGenerator(COMP_ID_GEN);
		this.components = Objects.requireNonNull(hz).getMap(COMP_MAP);
	}

	/**
	 * Adds an interceptor which gets notified if certain components will
	 * change. He then can perform actions like update the clients in range
	 * about the occuring component change.
	 * 
	 * @param interceptor
	 *            The interceptor to listen to certain triggering events.
	 */
	public void addInterceptor(ComponentInterceptor<? extends Component> interceptor) {
		Objects.requireNonNull(interceptor);

		final Class<? extends Component> triggerType = interceptor.getTriggerType();

		if (!interceptors.containsKey(triggerType)) {
			interceptors.put(triggerType, new ArrayList<>());
		}

		interceptors.get(triggerType).add(interceptor);
	}

	/**
	 * Returns a new ID either from the internal pool of the id generator of
	 * hazelcast.
	 * 
	 * @return A new, currently unused id.
	 */
	private long getNewEntityId() {
		final long id = entityIdGen.newId();

		if (id == 0) {
			return entityIdGen.newId();
		}

		return id;
	}

	/**
	 * Returns a fresh entity which can be used inside the system. It already
	 * has a unique ID and can be used to persist date.
	 * 
	 * @return
	 */
	public Entity newEntity() {
		final Entity entity = new Entity(getNewEntityId());
		saveEntity(entity);
		return entity;
	}

	/**
	 * Deletes the entity
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(Entity entity) {
		Objects.requireNonNull(entity);
		LOG.trace("Deleting entity: {}", entity.getId());
		try {
			entities.lock(entity.getId());

			// Delete all components.
			deleteAllComponents(entity);
			entities.delete(entity.getId());

		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Deletes the entity given by its id. This is a alias for
	 * {@link #delete(Entity)}.
	 * 
	 * @param entity
	 *            Removes the entity.
	 */
	public void delete(long entityId) {
		delete(entities.get(entityId));
	}

	/**
	 * Puts the entity into the memory database for access for the bestia
	 * system.
	 * 
	 * @param entity
	 *            The entity to put into the memory database.
	 */
	private void saveEntity(Entity entity) {
		LOG.trace("Saving entity: {}", entity);
		
		// Remove entity context since it can not be serialized.
		try {
			entities.lock(entity.getId());
			entities.put(entity.getId(), entity);
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Returns the ID entity with the given ID.
	 * 
	 * @param entityId
	 * @return The {@link Entity} or NULL if no such id is stored.
	 */
	public Entity getEntity(long entityId) {
		entities.lock(entityId);
		try {
			final Entity e = entities.get(entityId);
			return e;
		} finally {
			entities.unlock(entityId);
		}
	}

	/**
	 * Gets all entities with the given ids.
	 * 
	 * @param ids
	 *            IDs to return all entities.
	 * @return All entities which possess the given IDs.
	 */
	public Map<Long, Entity> getAllEntities(Set<Long> ids) {
		return entities.getAll(ids);
	}

	/**
	 * Returns all entities which are currently colliding with the given entity.
	 * The entity to check for collisions must implement the position component.
	 * Also only entities implementing position components can be checked
	 * against collision. If the entity does not have a
	 * {@link PositionComponent} an empty set will be returned.
	 * 
	 * This is basically a shortcut for a rather frequently called operation for
	 * scripts to get entities colliding with script entities. It is similar to
	 * {@link #getCollidingEntities(CollisionShape)}.
	 * 
	 * @param entity
	 * @return
	 */
	public Set<Entity> getCollidingEntities(Entity entity) {
		LOG.trace("Finding all colliding entities for: {}.", entity);

		final Optional<PositionComponent> posComp = getComponent(entity, PositionComponent.class);

		if (!posComp.isPresent()) {
			return Collections.emptySet();
		}

		final CollisionShape shape = posComp.get().getShape();
		Set<Entity> collidingEntities = getCollidingEntities(shape);

		LOG.trace("Found colliding entities: {}.", collidingEntities);

		return collidingEntities;
	}

	/**
	 * Returns all the entities which are in range. The detected collision
	 * entities will have a {@link PositionComponent} for sure. Other components
	 * are optional.
	 * 
	 * @param area
	 *            The area in which the looked up entities lie.
	 * @return All entities contained in the area.
	 */
	public Set<Entity> getCollidingEntities(CollisionShape area) {

		// TODO Das muss noch effektiver gestaltet werden.
		final Set<Entity> colliders = new HashSet<>();

		entities.forEach((id, entity) -> {
			getComponent(entity, PositionComponent.class).ifPresent(posComp -> {
				if (posComp.getShape().collide(area)) {
					colliders.add(entity);
				}
			});
		});

		return colliders;
	}

	/**
	 * Alias to {@link #getComponent(Entity, Class)}.
	 * 
	 * @param entityId
	 *            The ID of the entity.
	 * @param clazz
	 *            The class of the {@link Component} to retrieve.
	 * @return The component if it was attached to this entity.
	 */
	public <T extends Component> Optional<T> getComponent(long entityId, Class<T> clazz) {

		final Entity e = getEntity(entityId);

		if (e == null) {
			return Optional.empty();
		}

		return getComponent(e, clazz);
	}

	public <T extends Component> Optional<T> getComponent(Entity e, Class<T> clazz) {
		Objects.requireNonNull(e);
		Objects.requireNonNull(clazz);

		LOG.trace("Getting component {} from entity: {}", clazz.getSimpleName(), e);

		@SuppressWarnings("unchecked")
		final long compId = e.getComponentId((Class<Component>) clazz);

		if (compId == 0) {
			return Optional.empty();
		}

		final Component comp;

		try {
			components.lock(compId);
			comp = components.get(compId);
		} finally {
			components.unlock(compId);
		}

		if (comp == null || !comp.getClass().isAssignableFrom(clazz)) {
			return Optional.empty();
		}

		return Optional.of(clazz.cast(comp));
	}

	/**
	 * A new component will be created and added to the entity. All components
	 * must have a constructor which only accepts a long value as an id.
	 * 
	 * @param entityId
	 * @param clazz
	 * @return
	 */
	public <T extends Component> T addComponent(Entity entity, Class<T> clazz) {
		Objects.requireNonNull(entity);
		Objects.requireNonNull(clazz);

		final Component comp;
		try {
			@SuppressWarnings("unchecked")
			Constructor<Component> ctor = (Constructor<Component>) clazz.getConstructor(long.class, long.class);
			comp = ctor.newInstance(getId(), entity.getId());
		} catch (Exception ex) {
			LOG.error("Could not instantiate component.", ex);
			throw new IllegalArgumentException(ex);
		}

		// Add component to entity and to the comp map.
		final long compId = comp.getId();
		try {
			components.lock(compId);
			components.put(compId, comp);
			entity.addComponent(comp);
			saveEntity(entity);
			LOG.trace("Added component {} to entity id: {}", comp, entity.getId());
		} finally {
			components.unlock(compId);
		}

		return clazz.cast(comp);
	}

	public void interceptCreatedComponents(Entity entity) {

		LOG.debug("Intercepting all components (created) for: {}.", entity);

		for (Component comp : getAllComponents(entity)) {
			// Check possible interceptors.
			if (interceptors.containsKey(comp.getClass())) {
				interceptors.get(comp.getClass()).forEach(intercep -> {
					// Need to cast so we dont get problems with typings.
					intercep.triggerCreateAction(this, entity, comp);
				});
			}
		}

	}

	/**
	 * Updates the given component back into the database.
	 * 
	 * @param component
	 *            The component to be updated into the database.
	 */
	public void saveComponent(Component component) {
		Objects.requireNonNull(component);

		components.put(component.getId(), component);

		final Entity ownerEntity = getEntity(component.getEntityId());

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			interceptors.get(component.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerUpdateAction(this, ownerEntity, component);
			});
		}
	}

	/**
	 * @return Non 0 new id of a component.
	 */
	private long getId() {
		final long id = idGenerator.newId();
		if (id == 0) {
			return getId();
		} else {
			return id;
		}
	}

	/**
	 * Only removes the component from the entity and the system. Does not yet
	 * safe the entity. This is to avoid multiple saves to the entity when
	 * removing bulk components.
	 * 
	 * @param entity
	 * @param component
	 */
	private void prepareComponentRemove(Entity entity, Component component) {
		Objects.requireNonNull(entity);
		Objects.requireNonNull(component);

		LOG.trace("Removing component id {} from entity {}.", component.getId(), entity.getId());

		entity.removeComponent(component);
		
		try {
			components.lock(component.getId());
			components.remove(component.getId());
		} finally {
			components.unlock(component.getId());
		}
		
		component.setEntityId(0);
	}

	/**
	 * Deletes a specific component from this entity. Interceptors which were
	 * registered for this component are getting called.
	 * 
	 * @param entity
	 *            The entity to delete the component from.
	 * @param component
	 *            The component to delete.
	 */
	public void deleteComponent(Component component) {
		final Entity entity = getEntity(component.getEntityId());

		LOG.trace("Removing component {} from entity {}.", component, entity);

		prepareComponentRemove(entity, component);

		saveEntity(entity);
	}

	/**
	 * Removes all the components from the entity.
	 * 
	 * @param entity
	 *            The entity to remove all components from.
	 */
	public void deleteAllComponents(Entity entity) {

		final Set<Long> componentIds = new HashSet<>(entity.getComponentIds());

		for (Long componentId : componentIds) {
			
			final Component comp = components.get(componentId);
			prepareComponentRemove(entity, comp);

		}

		saveEntity(entity);
	}

	/**
	 * Checks if the entity has the given component.
	 * 
	 * @param entity
	 *            The entity to check.
	 * @param clazz
	 *            The component class to check for.
	 * @return TRUE if the entity has the component. FALSE otherwise.
	 */
	public boolean hasComponent(Entity entity, Class<? extends Component> clazz) {
		Objects.requireNonNull(entity);
		Objects.requireNonNull(clazz);

		return entity.getComponentId(clazz) != 0;
	}

	/**
	 * Returns all components of this entity.
	 * 
	 * @param entity
	 *            The entity which components will get returned.
	 * @return A collection of all components from this entity.
	 */
	public Collection<Component> getAllComponents(Entity entity) {

		return Objects.requireNonNull(entity)
				.getComponentIds()
				.stream()
				.map(components::get)
				.collect(Collectors.toList());
	}
}
