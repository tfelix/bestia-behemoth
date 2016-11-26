package net.bestia.zoneserver.service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

import net.bestia.model.shape.Rect;
import net.bestia.zoneserver.entity.PlayerBestiaEntity;
import net.bestia.zoneserver.entity.traits.IdEntity;

/**
 * This service manages and queries the active entities inside the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class PlayerEntityService {

	private final static String ACTIVE_ENTITIES_KEY = "active_entities";

	private final MultiMap<Long, Long> playerBestiaEntitiesIds;
	private final IMap<Long, Long> activeEntities;
	private final EntityService entityService;

	@Autowired
	public PlayerEntityService(HazelcastInstance hz, EntityService entityService) {

		this.activeEntities = hz.getMap(ACTIVE_ENTITIES_KEY);
		this.playerBestiaEntitiesIds = hz.getMultiMap("playerBestiaIds");
		this.entityService = Objects.requireNonNull(entityService);
	}

	/**
	 * Sets the entity id as the active player bestia for the given account id.
	 * 
	 * @param accId
	 * @param activeEntityId
	 */
	public void setActiveEntity(long accId, long activeEntityId) {
		// Remove the active flag from the last active player bestia.
		Long lastActive = activeEntities.get(accId);
		activeEntities.put(accId, activeEntityId);

		PlayerBestiaEntity pbe = null;

		// Last active might be null if no previous bestia was active.
		if (lastActive != null) {
			pbe = (PlayerBestiaEntity) entityService.getEntity(lastActive);
			pbe.setActive(false);
			entityService.put(pbe);
		}

		pbe = (PlayerBestiaEntity) entityService.getEntity(activeEntityId);
		pbe.setActive(true);
		entityService.put(pbe);
	}

	/**
	 * Returns the active player bestia entity for the given account it.
	 * 
	 * @param accId
	 *            The account id.
	 * @return The active {@link PlayerBestiaEntity} of this account or null.
	 */
	public PlayerBestiaEntity getActivePlayerEntity(long accId) {
		final Long entityId = activeEntities.get(accId);

		if (entityId == null) {
			return null;
		}

		final IdEntity entity = entityService.getEntity(entityId);

		if (entity == null || !(entity instanceof PlayerBestiaEntity)) {
			return null;
		}

		return (PlayerBestiaEntity) entity;
	}

	/**
	 * Returns a list with account ids of active player accounts in range.
	 * 
	 * @param range
	 * @return
	 */
	public List<Long> getActiveAccountIdsInRange(Rect range) {
		List<PlayerBestiaEntity> pbe = entityService.getEntitiesInRange(range, PlayerBestiaEntity.class)
				.stream()
				.map(x -> (PlayerBestiaEntity) x)
				.collect(Collectors.toList());

		return pbe.stream()
				.filter(x -> x.isActive())
				.map(x -> x.getAccountId())
				.collect(Collectors.toList());
	}

	/**
	 * Returns all the active player bestias for a given account.
	 * 
	 * @param accId
	 * @return The set of player bestia entities of a single player.
	 */
	public Set<PlayerBestiaEntity> getPlayerBestiaEntities(long accId) {

		final Collection<Long> ids = playerBestiaEntitiesIds.get(accId);
		return entityService.getAll(new HashSet<>(ids))
				.values()
				.parallelStream()
				.filter(x -> x instanceof PlayerBestiaEntity)
				.map(x -> (PlayerBestiaEntity) x)
				.collect(Collectors.toSet());
	}

	/**
	 * Inserts the given player bestias into the cache. The player bestias must
	 * not be from the same player account. This will be taken care off.
	 * 
	 * @param pb
	 *            A collection of player bestias.
	 */
	public void putPlayerBestiaEntities(Collection<PlayerBestiaEntity> pb) {

		final Map<Long, List<PlayerBestiaEntity>> byAccId = pb.stream()
				.collect(Collectors.groupingBy(PlayerBestiaEntity::getAccountId));

		byAccId.forEach((accId, pbes) -> {
			pbes.forEach(pbe -> {
				entityService.put(pbe);
				playerBestiaEntitiesIds.put(accId, pbe.getId());
			});
		});
	}

	/**
	 * Deletes all player bestias for this given account id from the system.
	 * 
	 * @param accId
	 *            The account id to delete all bestias from.
	 */
	public void removePlayerBestias(long accId) {
		// First get all ids of player bestias.
		final Collection<Long> ids = playerBestiaEntitiesIds.get(accId);
		ids.forEach(id -> entityService.delete(id));
		playerBestiaEntitiesIds.remove(accId);
		// Remove the active bestia.
		activeEntities.remove(accId);
	}
}
