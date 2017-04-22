package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.entity.components.PlayerComponent;

/**
 * This service manages and queries the active entities inside the game.
 * 
 * @author Thomas Felix <thomas.felix@tfelix.de>
 *
 */
@Service
public class PlayerEntityService {

	private final static String ACTIVE_ENTITIES_KEY = "entities.player.active";
	private final static String PLAYER_ENTITIES_KEY = "entities.player";

	private final MultiMap<Long, Long> playerBestiaEntitiesIds;
	private final IMap<Long, Long> activeEntities;
	private final EntityService entityService;
	private final ComponentService componentService;

	@Autowired
	public PlayerEntityService(HazelcastInstance hz, EntityService entityService,
			ComponentService componentService) {

		this.activeEntities = hz.getMap(ACTIVE_ENTITIES_KEY);
		this.playerBestiaEntitiesIds = hz.getMultiMap(PLAYER_ENTITIES_KEY);
		this.entityService = Objects.requireNonNull(entityService);
		this.componentService = Objects.requireNonNull(componentService);
	}

	/**
	 * Sets the entity id as the active player bestia for the given account id.
	 * 
	 * @param accId
	 * @param activeEntityId
	 */
	public void setActiveEntity(long accId, long activeEntityId) {
		// Remove the active flag from the last active player bestia.
		activeEntities.put(accId, activeEntityId);
	}

	/**
	 * Checks if the given entity id is the active player entity.
	 * 
	 * @param accId
	 *            The account to check the active entity.
	 * @param activeEntityId
	 *            The entity which should be checked if its active.
	 * @return TRUE if this is the active entity. FALSE otherwise.
	 */
	public boolean isActiveEntity(long accId, long activeEntityId) {
		Long active = activeEntities.get(accId);
		return active != null && active == activeEntityId;
	}

	/**
	 * Returns the active player bestia entity for the given account it.
	 * 
	 * @param accId
	 *            The account id.
	 * @return The active {@link PlayerEntity} of this account or null.
	 */
	public Entity getActivePlayerEntity(long accId) {
		final Long entityId = activeEntities.get(accId);

		if (entityId == null) {
			return null;
		}

		final Entity entity = entityService.getEntity(entityId);

		if (entity == null) {
			return null;
		}

		return entity;
	}

	/**
	 * Returns a list of account ids from players which active bestia entity is
	 * inside the given rect. This is especially used and importand when update
	 * messages must be send to all players inside a given area.
	 * 
	 * @param range
	 * @return
	 */
	public List<Long> getActiveAccountIdsInRange(Rect range) {

		List<PlayerComponent> pbe = entityService.getEntitiesInRange(range, PlayerComponent.class)
				.stream()
				.map(x -> componentService.getComponent(x, PlayerComponent.class))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());

		return pbe.stream()
				.filter(x -> isActiveEntity(x.getOwnerAccountId(), x.getId()))
				.map(x -> x.getOwnerAccountId())
				.collect(Collectors.toList());
	}

	/**
	 * Returns all player bestias for a given account.
	 * 
	 * @param accId
	 * @return The set of player bestia entities of a single player.
	 */
	public Set<Entity> getPlayerEntities(long accId) {

		final Collection<Long> ids = playerBestiaEntitiesIds.get(accId);
		return entityService.getAllEntities(new HashSet<>(ids))
				.values()
				.stream()
				.filter(x -> componentService.hasComponent(x, PlayerComponent.class))
				.collect(Collectors.toSet());
	}

	/**
	 * Inserts the given player bestias into the cache. The player bestias are
	 * not required be from the same player account. This will be taken care
	 * off.
	 * 
	 * @param pb
	 *            A collection of player bestias.
	 */
	public void putPlayerEntities(Collection<Entity> pb) {
		pb.stream()
				.filter(x -> componentService.hasComponent(x, PlayerComponent.class))
				.forEach(e -> {
					Optional<PlayerComponent> playerComp = componentService.getComponent(e, PlayerComponent.class);
					
					final long accId = playerComp.get().getOwnerAccountId();				
					entityService.save(e);
					playerBestiaEntitiesIds.put(accId, e.getId());
				});
	}

	/**
	 * Checks if the given account owns this entity.
	 * 
	 * @param accId
	 *            An account id.
	 * @param entityId
	 *            The entity for which ownership should be checked.
	 * @return TRUE if the player owns this entity. FALSE if not or the
	 *         account/entity was not found.
	 */
	public boolean hasPlayerEntity(long accId, long entityId) {
		return playerBestiaEntitiesIds.containsEntry(accId, entityId);
	}

	/**
	 * Puts a single {@link PlayerEntity} into the cache.
	 * 
	 * @param pbe
	 *            The player entity to put into the cache.
	 */
	public void putPlayerEntity(Entity entity) {

		final Optional<PlayerComponent> playerComp = componentService.getComponent(entity, PlayerComponent.class);

		if (playerComp.isPresent()) {
			// entityService.save(playerComp.get().);
			playerBestiaEntitiesIds.put(playerComp.get().getOwnerAccountId(), playerComp.get().getPlayerBestiaId());
		}
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