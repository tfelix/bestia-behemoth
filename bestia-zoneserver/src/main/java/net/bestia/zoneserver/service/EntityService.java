package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
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
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
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

	private final static String ENTITIES_KEY = "entities";

	private final CacheManager<Long, Set<Long>> playerBestiaEntitiesIds;

	private HazelcastInstance hazelcastInstance;
	private final IMap<Long, IdEntity> entities;

	@Autowired
	public EntityService(HazelcastInstance hz) {

		this.hazelcastInstance = Objects.requireNonNull(hz);
		this.entities = hazelcastInstance.getMap(ENTITIES_KEY);
		this.playerBestiaEntitiesIds = new CacheManager<>("playerBestiaIds", hz);
	}

	public Set<PlayerBestiaEntity> getPlayerBestiaEntities(long accId) {

		final Set<Long> ids = playerBestiaEntitiesIds.get(accId);
		return entities.getAll(ids).values().parallelStream()
				.filter(x -> x instanceof PlayerBestiaEntity)
				.map(x -> (PlayerBestiaEntity) x)
				.collect(Collectors.toSet());
	}

	public void putPlayerBestias(PlayerBestiaEntity pb) {

		if (!playerBestiaEntitiesIds.containsKey(pb.getAccountId())) {
			playerBestiaEntitiesIds.set(pb.getAccountId(), new HashSet<>());
		}

		playerBestiaEntitiesIds.get(pb.getAccountId()).add(pb.getId());
		save(pb);
	}

	public void removePlayerBestias(long accId) {
		// First get all ids of player bestias.
		final Set<Long> ids = playerBestiaEntitiesIds.get(accId);
		ids.forEach(id -> delete(id));
		playerBestiaEntitiesIds.remove(accId);
	}

	public void save(IdEntity entity) {
		entities.put(entity.getId(), entity);
	}

	public void delete(long entityId) {
		entities.delete(entityId);
	}

	public void delete(IdEntity entity) {
		delete(entity.getId());
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
