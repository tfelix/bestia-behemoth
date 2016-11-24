package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IAtomicLong;
import com.hazelcast.core.IMap;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;

import net.bestia.model.map.Map;
import net.bestia.model.shape.Point;
import net.bestia.model.shape.Rect;
import net.bestia.zoneserver.entity.BaseEntity;
import net.bestia.zoneserver.entity.traits.IdEntity;
import net.bestia.zoneserver.entity.traits.Locatable;
import net.bestia.zoneserver.entity.traits.Visible;

/**
 * This service manages and queries the active entities inside the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class EntityService {

	private HazelcastInstance hazelcastInstance;
	private final IMap<Long, IdEntity> entities;
	private final IAtomicLong idCounter;

	@Autowired
	public EntityService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap("entities");
		this.idCounter = hazelcastInstance.getAtomicLong("entityIdCounter");
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
			long newId = idCounter.incrementAndGet();
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

		if(filterType == null) {
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

	public IdEntity getEntity(long entityId) {
		return entities.get(entityId);
	}

	public java.util.Map<Long, IdEntity> getAll(Set<Long> ids) {
		return entities.getAll(ids);
	}

	/**
	 * Finds all other entities which are in sight range of the given entity id.
	 * 
	 * @param entity
	 *            The entity to get all entities in sight range from.
	 * @return All {@link IdEntity} which are in sight range of the given
	 *         entity.
	 */
	public Collection<IdEntity> getEntitiesInSight(Locatable entity) {
		final Point pos = entity.getPosition();
		final Rect sightRect = new Rect(pos.getX() - Map.SIGHT_RANGE,
				pos.getY() - Map.SIGHT_RANGE,
				pos.getX() + Map.SIGHT_RANGE,
				pos.getY() + Map.SIGHT_RANGE);
		return getEntitiesInRange(sightRect);
	}

}
