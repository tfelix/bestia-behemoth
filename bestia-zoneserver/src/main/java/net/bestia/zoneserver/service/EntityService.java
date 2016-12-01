package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;
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
import com.hazelcast.query.Predicates;

import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.BaseEntity;
import net.bestia.zoneserver.entity.traits.IdEntity;
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
	private final IMap<Long, IdEntity> entities;
	private final IdGenerator idCounter;

	@Autowired
	public EntityService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap("entities");
		this.idCounter = hazelcastInstance.getIdGenerator("entities.id");
	}

	/**
	 * Puts the entity into the memory database for access for the bestia
	 * system.
	 * 
	 * @param entity
	 *            The entity to put into the memory database.
	 */
	public void put(IdEntity entity) {
		// Check if this id already exists.
		if (!entities.containsKey(entity.getId())) {
			long newId = idCounter.newId();
			entity.setId(newId);
		}
		entities.put(entity.getId(), entity);
	}

	public void delete(long entityId) {
		entities.delete(entityId);
	}

	public void delete(IdEntity entity) {
		delete(entity.getId());
	}

	public Collection<IdEntity> getEntitiesInRange(Rect area) {
		return getEntitiesInRange(area, null);
	}

	/**
	 * Looks for all entities in the given range but with the given class type.
	 * 
	 * @param area
	 * @param filterType
	 * @return
	 */
	@SuppressWarnings("rawtypes")
	public Collection<IdEntity> getEntitiesInRange(Rect area, Class<? extends BaseEntity> filterType) {
		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();

		final Predicate xPredicate = e.get("position.x").between(area.getX(), area.getX() + area.getWidth());
		final Predicate yPredicate = e.get("position.y").between(area.getY(), area.getY() + area.getHeight());

		if (filterType == null) {
			final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate);
			return entities.values(rangePredicate);
		} else {
			final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate, Predicates.instanceOf(filterType));
			return entities.values(rangePredicate);
		}

	}

	/**
	 * Finds all visible entities inside the queried rectangle.
	 * 
	 * @param area
	 *            The area to collect all {@link Visible} entities.
	 * @return A collection of the found entities.
	 */
	public Collection<IdEntity> getVisibleEntitiesInRange(Rect area) {
		return getEntitiesInRange(area)
				.parallelStream()
				.filter(x -> x instanceof Visible)
				.collect(Collectors.toList());
	}

	/**
	 * Returns the ID entity with the given ID.
	 * 
	 * @param entityId
	 * @return The {@link IdEntity} or NULL if no such id is stored.
	 */
	public IdEntity getEntity(long entityId) {
		return entities.get(entityId);
	}

	/**
	 * Finds all entities with the given ids.
	 * 
	 * @param ids
	 *            The ids to look for the entities.
	 * @return A {@link java.util.Map} of the ids and entities.
	 */
	public java.util.Map<Long, IdEntity> getAll(Set<Long> ids) {
		return entities.getAll(ids);
	}
}
