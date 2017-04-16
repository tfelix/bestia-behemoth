package net.bestia.zoneserver.entity.ecs;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;

@Service
public class EcsEntityService {

	private final static String ECS_ENTITY_MAP = "entities.ecs";
	private final static String ENTITIES_ID_GEN = "entities.id";

	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final ComponentService componentService;

	@Autowired
	public EcsEntityService(HazelcastInstance hz, ComponentService compService) {

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
	 * Deletes the entity given by its id.
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(Entity entity) {
		delete(entity.getId());
	}

	/**
	 * The entity to remove.
	 * 
	 * @param entity
	 *            Removes the entity.
	 */
	public void delete(long entityId) {
		entities.lock(entityId);
		try {
			entities.delete(entityId);
		} finally {
			entities.unlock(entityId);
		}
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

	/*
	public <T> Optional<T> getComponent(long entityId, Class<T> clazz) {
/*
		return entityComponents.get(entityId)
				.stream()
				.filter(c -> clazz.isInstance(c))
				.map(c -> clazz.cast(c))
				.findFirst();
	}*/
}