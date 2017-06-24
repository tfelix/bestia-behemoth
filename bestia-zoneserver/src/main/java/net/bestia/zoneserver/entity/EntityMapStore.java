package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.hazelcast.core.MapStore;

/**
 * Specialized class which enables the entities to be persistedly stored into a
 * persistent database. This is needed if the bestia server goes down or is shut
 * down so the world keeps persisted and can be reloaded.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EntityMapStore implements MapStore<Long, Entity> {

	private final static Logger LOG = LoggerFactory.getLogger(EntityMapStore.class);
	private final EntityPersistService entityPersistService;

	@Autowired
	public EntityMapStore(EntityPersistService entityPersistService) {

		this.entityPersistService = Objects.requireNonNull(entityPersistService);
	}

	@Override
	public synchronized Entity load(Long id) {

		LOG.trace("Loading entity: {}.", id);

		final Entity entity = entityPersistService.load(id);
		return entity;
	}

	@Override
	public synchronized Map<Long, Entity> loadAll(Collection<Long> ids) {

		final Map<Long, Entity> entities = new HashMap<>();

		for (Long id : ids) {
			final Entity e = entityPersistService.load(id);
			entities.put(id, e);
		}

		return entities;
	}

	@Override
	public synchronized Iterable<Long> loadAllKeys() {
		// All loading not supported at the moment since we can not easily
		// iterate over the SQL keys. Maybe implement later when there is more
		// time.
		return null;
	}

	@Override
	public synchronized void delete(Long id) {

		LOG.trace("Deleting entity: {}.", id);

		entityPersistService.deleteEntity(id);
	}

	@Override
	public synchronized void deleteAll(Collection<Long> ids) {

		LOG.trace("Deleting all {} entities.", ids.size());

		for (Long id : ids) {
			entityPersistService.deleteEntity(id);
		}

	}

	@Override
	public synchronized void store(Long id, Entity entity) {

		LOG.trace("Persisting entity: {}", entity);

		entityPersistService.store(entity);

	}

	@Override
	public synchronized void storeAll(Map<Long, Entity> entities) {

		LOG.trace("Persisting {} entities.", entities.size());

		for (Entity entity : entities.values()) {
			entityPersistService.store(entity);
		}

	}

}
