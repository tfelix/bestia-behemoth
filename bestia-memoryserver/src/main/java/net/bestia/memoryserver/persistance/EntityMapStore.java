package net.bestia.memoryserver.persistance;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Component;

import com.hazelcast.core.MapStore;

import net.bestia.entity.Entity;

/**
 * Specialized class which enables the entities to be persistedly stored into a
 * database. This is needed if the bestia server goes down or is shut down so
 * the world keeps persisted and can be reloaded. This store classes synchronize
 * in memory objects with persistent databases.
 * 
 * @author Thomas Felix
 *
 */
@Component
public class EntityMapStore implements MapStore<Long, Entity> {

	private final static Logger LOG = LoggerFactory.getLogger(EntityMapStore.class);
	private final EntityPersistService entityPersistService;

	/**
	 * Ctor. EntityPersistService must be marked with Lazy since it has a
	 * indirect dependency to a {@link CrudRepository} which in turn uses a
	 * hazelcast instance because of caching. This forms a circular dependency.
	 * 
	 * @param entityPersistService
	 */
	@Autowired
	public EntityMapStore(@Lazy EntityPersistService entityPersistService) {

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

		entityPersistService.delete(id);
	}

	@Override
	public synchronized void deleteAll(Collection<Long> ids) {

		LOG.trace("Deleting all {} entities.", ids.size());

		for (Long id : ids) {
			entityPersistService.delete(id);
		}

	}

	@Override
	public synchronized void store(Long id, Entity entity) {

		LOG.trace("Persisting entity: {}", entity);

		entityPersistService.store(entity);

	}

	@Override
	public synchronized void storeAll(Map<Long, Entity> entities) {

		for (Entity entity : entities.values()) {
			entityPersistService.store(entity);
		}

	}

}
