package net.bestia.zoneserver.entity;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;

import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.components.Component;
import net.bestia.zoneserver.entity.components.PositionComponent;

@Service
public class EntityService {

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_ID_GEN = "entities.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final ComponentService componentService;

	@Autowired
	public EntityService(HazelcastInstance hz, ComponentService compService) {

		Objects.requireNonNull(hz);

		idCounter = hz.getIdGenerator(ENTITIES_ID_GEN);
		entities = hz.getMap(ECS_ENTITY_MAP);

		this.componentService = Objects.requireNonNull(compService);
	}

	/**
	 * Returns a new ID either from the internal pool of the id generator of
	 * hazelcast.
	 * 
	 * @return A new, currently unused id.
	 */
	private long getNewEntityId() {
		return idCounter.newId();
	}

	/**
	 * Returns a fresh entity which can be used inside the system. It already
	 * has a unique ID and can be used to persist date.
	 * 
	 * @return
	 */
	public Entity newEntity() {
		return new Entity(getNewEntityId());
	}

	/**
	 * Deletes the entity
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(Entity entity) {
		Objects.requireNonNull(entity);

		entities.lock(entity.getId());
		try {
			// Delete all components.
			componentService.removeAllComponents(entity);
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
	 * system. Before saving (and thus serializing it). The context must be
	 * removed.
	 * 
	 * @param entity
	 *            The entity to put into the memory database.
	 */
	public void save(Entity entity) {
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
		EntryObject e = new PredicateBuilder().getEntryObject();
		@SuppressWarnings("rawtypes")
		Predicate posPred = e.get("components").in(PositionComponent.class.getName());
		return entities.values(posPred).stream().filter(entity -> {
			Optional<PositionComponent> comp = componentService.getComponent(entity.getId(), PositionComponent.class);
			if (!comp.isPresent()) {
				return false;
			}
			return comp.get().getShape().collide(area);
		}).collect(Collectors.toSet());
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
}
