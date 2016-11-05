package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.query.EntryObject;
import com.hazelcast.query.Predicate;
import com.hazelcast.query.PredicateBuilder;
import com.hazelcast.query.Predicates;

import net.bestia.model.shape.Rect;
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

	private final static String ENTITIES_KEY = "entities";

	private HazelcastInstance hazelcastInstance;
	private final IMap<Long, IdEntity> entities;

	@Autowired
	public EntityService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap(ENTITIES_KEY);
	}
	
	public void save(IdEntity entity) {
		entities.put(entity.getId(),entity);
	}

	@SuppressWarnings("rawtypes")
	public Collection<IdEntity> getEntitiesInRange(Rect area) {
		// Build the query.
		final EntryObject e = new PredicateBuilder().getEntryObject();

		final Predicate xPredicate = e.get("position.x").between(area.getX(), area.getX() + area.getWidth());
		final Predicate yPredicate = e.get("position.y").between(area.getY(), area.getY() + area.getHeight());

		final Predicate rangePredicate = Predicates.and(xPredicate, yPredicate);

		return entities.values(rangePredicate);
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

}
