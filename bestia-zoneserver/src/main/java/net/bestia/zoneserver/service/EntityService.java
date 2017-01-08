package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.IdGenerator;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;

import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.BaseEntity;
import net.bestia.zoneserver.entity.EntityContext;
import net.bestia.zoneserver.entity.traits.Entity;
import net.bestia.zoneserver.entity.traits.Visible;

/**
 * This service manages and queries the active entities inside the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class EntityService {

	private final HazelcastInstance hazelcastInstance;
	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final EntityContext entityContext;
	private final Lock lock;

	@Autowired
	public EntityService(HazelcastInstance hz, EntityContext entityContext) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap("entities");
		this.idCounter = hazelcastInstance.getIdGenerator("entities.id");
		this.entityContext = Objects.requireNonNull(entityContext);
		this.lock = hz.getLock("entities.lock");
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
		entity.setEntityContext(null);
		try {
			lock.lock();
			// Check if this id already exists.
			if (!entities.containsKey(entity.getId())) {
				long newId = idCounter.newId();
				entity.setId(newId);
			}
			entities.put(entity.getId(), entity);
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Deletes the entity given by its id.
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
	 */
	public void delete(long entityId) {
		try {
			lock.lock();
			entities.delete(entityId);
		} finally {
			lock.unlock();
		}

	}

	/**
	 * The entity to remove.
	 * 
	 * @param entity
	 *            Removes the entity.
	 */
	public void delete(Entity entity) {
		delete(entity.getId());
	}

	/**
	 * Gets all entities inside the range of the given rectangular area.
	 * 
	 * @param area
	 * @return All entities inside this arera.
	 */
	public Collection<Entity> getEntitiesInRange(Rect area) {
		return getEntitiesInRange(area, null);
	}

	/**
	 * Looks for all entities in the given range but only with the given class
	 * type.
	 * 
	 * @param area
	 *            The area to look for the entities.
	 * @param filterType
	 *            Gets entities only with this type.
	 * @return All entities which are in range and are an instance of the given
	 *         filter type.
	 */
	@SuppressWarnings("rawtypes")
	public Collection<Entity> getEntitiesInRange(Rect area, Class<? extends BaseEntity> filterType) {
		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();

		final Predicate xPredicate = e.get("position.x").between(area.getX(), area.getX() + area.getWidth());
		final Predicate yPredicate = e.get("position.y").between(area.getY(), area.getY() + area.getHeight());

		final Collection<Entity> found;

		try {
			lock.lock();

			if (filterType == null) {
				final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate);
				found = entities.values(rangePredicate);
			} else {
				final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate,
						Predicates.instanceOf(filterType));
				found = entities.values(rangePredicate);
			}

			// Set ctx.
			found.forEach(x -> x.setEntityContext(entityContext));

			return found;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Finds all visible entities inside the queried rectangle.
	 * 
	 * @param area
	 *            The area to collect all {@link Visible} entities.
	 * @return A collection of the found entities.
	 */
	public Collection<Entity> getVisibleEntitiesInRange(Rect area) {
		return getEntitiesInRange(area)
				.parallelStream()
				.filter(x -> x instanceof Visible)
				.collect(Collectors.toList());
	}

	/**
	 * Gets the entity and performs
	 * 
	 * @param entityId
	 * @param clazz
	 * @return
	 */
	public <T> T getEntity(long entityId, Class<T> clazz) throws ClassCastException {
		try {
			lock.lock();
			final Entity e = entities.get(entityId);

			if (e == null) {
				return null;
			}

			// Check instance.
			if (clazz.isAssignableFrom(e.getClass())) {
				e.setEntityContext(entityContext);
				return clazz.cast(e);
			} else {
				throw new ClassCastException("Found IdEntity is not of type " + clazz.getName());
			}
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Returns the ID entity with the given ID.
	 * 
	 * @param entityId
	 * @return The {@link Entity} or NULL if no such id is stored.
	 */
	public Entity getEntity(long entityId) {
		try {
			lock.lock();
			final Entity e = entities.get(entityId);
			e.setEntityContext(entityContext);
			return e;
		} finally {
			lock.unlock();
		}
	}

	/**
	 * Finds all entities with the given ids.
	 * 
	 * @param ids
	 *            The ids to look for the entities.
	 * @return A {@link java.util.Map} of the ids and entities.
	 */
	public java.util.Map<Long, Entity> getAll(Set<Long> ids) {
		try {
			lock.lock();
			java.util.Map<Long, Entity> es = entities.getAll(ids);
			es.entrySet().forEach(x -> x.getValue().setEntityContext(entityContext));
			return es;
		} finally {
			lock.unlock();
		}

	}
}
