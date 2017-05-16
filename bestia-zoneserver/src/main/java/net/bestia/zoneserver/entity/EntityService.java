package net.bestia.zoneserver.entity;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashSet;
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

import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.PositionComponent;

@Service
public class EntityService {

	private static final Logger LOG = LoggerFactory.getLogger(EntityService.class);

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_ID_GEN = "entities.id";
	private static final String COMP_MAP = "components";
	private static final String COMP_ID = "components.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator entityIdGen;
	private final IMap<Long, Component> components;
	private final IdGenerator idGenerator;

	@Autowired
	public EntityService(HazelcastInstance hz) {

		Objects.requireNonNull(hz);

		this.entityIdGen = hz.getIdGenerator(ENTITIES_ID_GEN);
		this.entities = hz.getMap(ECS_ENTITY_MAP);
		this.idGenerator = hz.getIdGenerator(COMP_ID);
		this.components = Objects.requireNonNull(hz).getMap(COMP_MAP);
	}

	/**
	 * Returns a new ID either from the internal pool of the id generator of
	 * hazelcast.
	 * 
	 * @return A new, currently unused id.
	 */
	private long getNewEntityId() {
		final long id = entityIdGen.newId();
		
		if(id == 0) {
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

		entities.lock(entity.getId());
		try {
			// Delete all components.
			removeAllComponents(entity);
			entities.delete(entity.getId());
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Deletes the entity given by its id.
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
			final Entity e = entities.get(entityId);
			return e;
		} finally {
			entities.unlock(entityId);
		}
	}

	public Map<Long, Entity> getAllEntities(Set<Long> ids) {
		return entities.getAll(ids);
	}

	/**
	 * Returns all the entities which are in range.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area) {

		// TODO Das muss noch effektiver gestaltet werden.
		Set<Entity> colliders = new HashSet<>();
		entities.forEach((id, entity) -> {
			getComponent(entity.getId(), PositionComponent.class).ifPresent(posComp -> {
				if (posComp.getShape().collide(area)) {
					colliders.add(entity);
				}
			});
		});

		return colliders;
	}

	/**
	 * Returns all the entities which are in range and have a certain component.
	 * Note that they ALWAYS must have a position component in order to get
	 * localized.
	 * 
	 * @param area
	 * @return
	 */
	public Set<Entity> getEntitiesInRange(Rect area, Class<? extends Component> clazz) {
		final Set<Class<? extends Component>> comps = new HashSet<>(Arrays.asList(clazz));
		return getEntitiesInRange(area).stream().filter(x -> comps.contains(x.getClass())).collect(Collectors.toSet());
	}

	

	public <T extends Component> Optional<T> getComponent(long entityId, Class<T> clazz) {

		final Entity e = getEntity(entityId);

		if (e == null) {
			return Optional.empty();
		}

		return getComponent(e, clazz);
	}

	public <T extends Component> Optional<T> getComponent(Entity e, Class<T> clazz) {
		Objects.requireNonNull(e);
		
		LOG.trace("Getting component {} from entity: {}", clazz.getSimpleName(), e);

		@SuppressWarnings("unchecked")
		final long compId = e.getComponentId((Class<Component>) clazz);

		if (compId == 0) {
			return Optional.empty();
		}

		final Component comp = components.get(compId);

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
		if (!Component.class.isAssignableFrom(clazz)) {
			throw new IllegalArgumentException("Only accept component classes.");
		}

		try {
			@SuppressWarnings("unchecked")
			Constructor<Component> ctor = (Constructor<Component>) clazz.getConstructor(long.class);
			final Component comp = ctor.newInstance(getId());

			// Add component to entity and to the comp map.
			components.put(comp.getId(), comp);
			entity.addComponent(comp);
			saveEntity(entity);
			return clazz.cast(comp);

		} catch (Exception ex) {
			LOG.error("Could not instantiate component.", ex);
			throw new IllegalArgumentException(ex);
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
	 * Removes all the components from the entity.
	 * 
	 * @param entity
	 *            The entity to remove all components from.
	 */
	public void removeAllComponents(Entity entity) {
		entity.getComponentIds().forEach(c -> {
			
			LOG.trace("Removing component id {} from entity {}.", c, entity.getId());
			
			components.remove(c);
			entity.removeComponent(c);
		});
		saveEntity(entity);
	}

	public boolean hasComponent(Entity entity, Class<? extends Component> clazz) {

		return entity.getComponentId(clazz) != 0;
	}
}
