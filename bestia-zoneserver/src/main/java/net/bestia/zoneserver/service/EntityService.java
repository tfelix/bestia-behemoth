package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import net.bestia.zoneserver.entity.EntityContext;
import net.bestia.zoneserver.entity.traits.Entity;

/**
 * This service manages and queries the active entities inside the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class EntityService {

	private static final Logger LOG = LoggerFactory.getLogger(EntityService.class);
	
	private final HazelcastInstance hazelcastInstance;
	private final IMap<Long, Entity> entities;
	private final IdGenerator idCounter;
	private final EntityContext entityContext;

	@Autowired
	public EntityService(HazelcastInstance hz, EntityContext entityContext) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap("entities");
		this.idCounter = hazelcastInstance.getIdGenerator("entities.id");
		this.entityContext = Objects.requireNonNull(entityContext);
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
		
		// Check if the entity needs a new unique id (if its a new one).
		if(entity.getId() == -1) {
			long newId = idCounter.newId();
			entity.setId(newId);
		}
		
		entities.lock(entity.getId());
		try {
			entities.put(entity.getId(), entity);
		} finally {
			entities.unlock(entity.getId());
		}
	}

	/**
	 * Deletes the entity given by its id.
	 * 
	 * @param entityId
	 *            The entity id to remove from the memory database.
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
		return getEntitiesInRange(area, Entity.class);
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
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public <T> Collection<T> getEntitiesInRange(Rect area, Class<T> filterType) {
		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();

		final Predicate xPredicate = e.get("position.x").between(area.getX(), area.getX() + area.getWidth());
		final Predicate yPredicate = e.get("position.y").between(area.getY(), area.getY() + area.getHeight());
		final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate, Predicates.instanceOf(filterType));

		final Collection<Entity> found = entities.values(rangePredicate);

		// Reassign ctx.
		found.forEach(x -> x.setEntityContext(entityContext));

		return (Collection<T>) found;
	}

	/**
	 * Gets the entity and performs a cast to the type requested. If the entity
	 * of the given id does not match the given type a
	 * {@link ClassCastException} is thrown.
	 * 
	 * @param entityId
	 *            The entity ID to look up.
	 * @param clazz
	 *            The class in which to cast the entity.
	 * @return The casted entity.
	 */
	public <T> T getEntity(long entityId, Class<T> clazz) throws ClassCastException {
		entities.lock(entityId);
		try {
			final Entity e = entities.get(entityId);

			if (e == null) {
				return null;
			}

			// Check instance.
			if (clazz.isAssignableFrom(e.getClass())) {
				e.setEntityContext(entityContext);
				return clazz.cast(e);
			} else {
				LOG.warn("Can not cast entity {} to class {}.", entityId, e.toString());
				return null;
			}
		} finally {
			entities.unlock(entityId);
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
			e.setEntityContext(entityContext);
			return e;
		} finally {
			entities.unlock(entityId);
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

		java.util.Map<Long, Entity> es = entities.getAll(ids);
		es.entrySet().forEach(x -> x.getValue().setEntityContext(entityContext));
		return es;
	}
}
