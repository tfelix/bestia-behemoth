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
		final Entity e = new Entity(getNewEntityId());
		saveEntity(e);
		return e;
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
		entities.lock(entity.getId());
		try {
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
		entities.lock(entity.getId());
		try {
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
			return entities.get(entityId);
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
			LOG.warn("Entity {} not found for component lookup: {}", entityId, clazz);
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

		components.lock(compId);
		try {
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
	 * Creates a new component which can be used with an entity. The component
	 * is not yet saved in the system and can be filled with data. When the
	 * component is filled it should be attached to an entity by calling
	 * {@link #attachComponent(Entity, Component)}. If multiple components
	 * should be attached at once on the entity then
	 * 
	 * @param clazz
	 * @return
	 */
	public <T extends Component> T newComponent(Class<T> clazz) {
		Objects.requireNonNull(clazz);

		final Component comp;
		try {
			@SuppressWarnings("unchecked")
			Constructor<Component> ctor = (Constructor<Component>) clazz.getConstructor(long.class);
			comp = ctor.newInstance(getId());
		} catch (Exception ex) {
			LOG.error("Could not instantiate component.", ex);
			throw new IllegalArgumentException(ex);
		}

		return clazz.cast(comp);
	}

	/**
	 * Re-attaches an existing component to an entity. The component must not be
	 * owned by an entity (its entity id must be set to 0).
	 * 
	 * @param e
	 * @param addedComp
	 */
	public void attachComponent(Entity e, Component comp) {
		Objects.requireNonNull(e);
		Objects.requireNonNull(comp);

		if (comp.getEntityId() != 0) {
			throw new IllegalArgumentException(
					"Component is already owned by an entity. Delete/Remove it first via EntityDeleter.");
		}

		// Add component to entity and to the comp map.
		final long compId = comp.getId();
		final long entityId = e.getId();

		comp.setEntityId(entityId);
		e.addComponent(comp);

		components.lock(compId);
		entities.lock(entityId);
		try {
			components.put(compId, comp);
			saveEntity(e);
			LOG.trace("Added component {} to entity id: {}", comp, e.getId());
		} finally {
			components.unlock(compId);
			entities.unlock(entityId);
		}

		interceptCreatedComponent(e, comp);
	}

	public void attachComponents(Entity e, Collection<Component> attachComponents) {
		Objects.requireNonNull(e);
		Objects.requireNonNull(components);

		// Add component to entity and to the comp map.
		final long entityId = e.getId();

		attachComponents.stream()
				.filter(c -> c.getEntityId() != 0 && c.getEntityId() != entityId)
				.findAny()
				.ifPresent(x -> {
					throw new IllegalArgumentException("Component was already attached to entity: " + x.toString());
				});

		try {
			entities.lock(entityId);
			for (Component comp : attachComponents) {
				final long compId = comp.getId();

				comp.setEntityId(entityId);
				e.addComponent(comp);

				components.lock(compId);
				try {
					components.put(compId, comp);
				} finally {
					components.unlock(compId);
				}
			}
			saveEntity(e);
		} finally {
			entities.unlock(entityId);
		}

		// After all is saved intercept the created components.
		attachComponents.forEach(c -> {
			interceptCreatedComponent(e, c);
		});
	}

	private void interceptCreatedComponent(Entity entity, Component createdComp) {

		// Check possible interceptors.
		if (interceptors.containsKey(createdComp.getClass())) {
			LOG.debug("Intercepting created component {} for: {}.", createdComp, entity);
			interceptors.get(createdComp.getClass()).forEach(intercep -> {
				// Need to cast so we dont get problems with typings.
				intercep.triggerCreateAction(this, entity, createdComp);
			});
		}
	}

	/**
	 * Saves the given component back into the database. Update of the
	 * interceptors is called. If the component is not attached to an entity it
	 * throws an exception.
	 * 
	 * @param component
	 *            The component to be updated into the database.
	 */
	public void updateComponent(Component component) {

		if (component.getEntityId() == 0) {
			throw new IllegalArgumentException("Component is not attached to entity. Call attachComponent first.");
		}

		components.lock(component.getId());
		try {
			components.put(component.getId(), component);
		} finally {
			components.unlock(component.getId());
		}

		// Check possible interceptors.
		if (interceptors.containsKey(component.getClass())) {
			final Entity ownerEntity = getEntity(component.getEntityId());
			LOG.debug("Intercepting update component {} for: {}.", component, ownerEntity);
			
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
	 * removing bulk components. Important: CALL {@link #saveEntity(Entity)}
	 * after using this private method!
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
			LOG.trace("Preparing to remove: {} from entity: {}", comp, entity);
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
