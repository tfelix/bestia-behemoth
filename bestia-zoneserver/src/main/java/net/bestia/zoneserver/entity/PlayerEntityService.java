package net.bestia.zoneserver.entity;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;

import net.bestia.messages.bestia.BestiaActivateMessage;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Rect;
import net.bestia.zoneserver.actor.ZoneAkkaApi;
import net.bestia.zoneserver.entity.components.PlayerComponent;
import net.bestia.zoneserver.service.PlayerBestiaService;

/**
 * This service manages the entities which are controlled by a player.
 * 
 * TODO Das hier ggf mit dem PlayerBestiaService kombinieren.
 * 
 * @author Thomas Felix
 *
 */
@Service
public class PlayerEntityService {

	private final static Logger LOG = LoggerFactory.getLogger(PlayerEntityService.class);

	private final static String ACTIVE_ENTITIES_KEY = "entities.player.active";
	private final static String PLAYER_ENTITIES_KEY = "entities.player";

	private final MultiMap<Long, Long> playerBestiaEntitiesIds;
	private final IMap<Long, Long> activeEntities;
	private final EntityService entityService;
	private final PlayerBestiaService playerBestiaService;
	private final ZoneAkkaApi akkaApi;

	@Autowired
	public PlayerEntityService(HazelcastInstance hz, EntityService entityService,
			PlayerBestiaService playerBestiaService, ZoneAkkaApi akkaApi) {

		this.activeEntities = hz.getMap(ACTIVE_ENTITIES_KEY);
		this.playerBestiaEntitiesIds = hz.getMultiMap(PLAYER_ENTITIES_KEY);
		this.entityService = Objects.requireNonNull(entityService);
		this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
		this.akkaApi = Objects.requireNonNull(akkaApi);
	}

	/**
	 * Sets the entity id as the active player bestia for the given account id.
	 * This will throw if the given entity id does not exist in the system.
	 * 
	 * @param accId
	 * @param activeEntityId
	 * @throws IllegalArgumentException
	 *             If the entity id does not exist.
	 */
	public void setActiveEntity(long accId, long activeEntityId) {
		if (null == entityService.getEntity(activeEntityId)) {
			throw new IllegalArgumentException("Active entity id was not found in the system. Add it first.");
		}

		// Check if this is a valid player bestia.
		final Entity activeEntity = entityService.getEntity(activeEntityId);

		final PlayerComponent playerComp = entityService.getComponent(activeEntity, PlayerComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		// Remove the active flag from the last active player bestia.
		activeEntities.put(accId, activeEntityId);

		LOG.debug("Activating entity id: {} for account: {}", activeEntityId, accId);

		final BestiaActivateMessage activateMsg = new BestiaActivateMessage(accId, activeEntityId,
				playerComp.getPlayerBestiaId());
		akkaApi.sendToClient(activateMsg);
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
				.map(x -> entityService.getComponent(x, PlayerComponent.class))
				.filter(Optional::isPresent)
				.map(Optional::get)
				.collect(Collectors.toList());

		return pbe.stream()
				.filter(x -> isActiveEntity(x.getOwnerAccountId(), x.getId()))
				.map(x -> x.getOwnerAccountId())
				.collect(Collectors.toList());
	}

	/**
	 * Returns all player bestia entities for a given account.
	 * 
	 * @param accId
	 * @return The set of player bestia entities of a single player.
	 */
	public Set<Entity> getPlayerEntities(long accId) {

		final Collection<Long> ids = playerBestiaEntitiesIds.get(accId);
		return entityService.getAllEntities(new HashSet<>(ids))
				.values()
				.stream()
				.filter(x -> entityService.hasComponent(x, PlayerComponent.class))
				.collect(Collectors.toSet());
	}

	/**
	 * Returns the master entity for a given account. There MUST be a master
	 * bestia registered for every account otherwise something very strange
	 * happened. The optional is empty if the account id was not known and no
	 * master was found for it.
	 * 
	 * @param accId
	 *            The account id to lookup the master.
	 * @return The found master entity.
	 */
	public Optional<Entity> getMasterEntity(long accId) {
		final PlayerBestia masterBestia = playerBestiaService.getMaster(accId);

		return getPlayerEntities(accId).stream().filter(e -> {
			Optional<PlayerComponent> pc = entityService.getComponent(e, PlayerComponent.class);
			if (!pc.isPresent()) {
				return false;
			}

			return pc.get().getPlayerBestiaId() == masterBestia.getId();
		}).findAny();
	}

	/**
	 * Inserts the given player bestias into the cache. The player bestias are
	 * not required be from the same player account. This will be taken care
	 * off.
	 * 
	 * This registers the given entities as player bestias. Only entities owning
	 * the component {@link PlayerComponent} will be processed by this call.
	 * 
	 * @param pb
	 *            A collection of player bestias.
	 */
	public void putPlayerEntities(Collection<Entity> pb) {
		pb.forEach(this::putPlayerEntity);
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
		Objects.requireNonNull(entity);

		// Can only add entities with player component.
		final PlayerComponent comp = entityService.getComponent(entity, PlayerComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final long accId = comp.getOwnerAccountId();
		final long entityId = entity.getId();

		LOG.debug("Adding player entity: accId: {}, entityId: {}.", accId, entityId);

		playerBestiaEntitiesIds.put(accId, entityId);
	}

	/**
	 * Deletes all player bestias for this given account id from the system.
	 * 
	 * @param accId
	 *            The account id to delete all bestias from.
	 */
	public void removePlayerBestias(long accId) {
		LOG.trace("Removing all bestias of player {}.", accId);
		// First get all ids of player bestias.
		final Collection<Long> ids = playerBestiaEntitiesIds.get(accId);
		ids.forEach(id -> entityService.delete(id));
		playerBestiaEntitiesIds.remove(accId);
		// Remove the active bestia.
		activeEntities.remove(accId);
	}

	/**
	 * Removes the given entity (player bestia) from the active system.
	 * 
	 * @param playerBestia
	 *            The player bestia entity to be removed.
	 * @return TRUE if the entity could be removed. FALSE otherwise.
	 */
	public boolean removePlayerBestia(Entity playerBestia) {

		Objects.requireNonNull(playerBestia);

		final PlayerComponent playerComp = entityService.getComponent(playerBestia, PlayerComponent.class)
				.orElseThrow(IllegalArgumentException::new);

		final long accId = playerComp.getOwnerAccountId();

		// Dont remove if its the last bestia.
		if (playerBestiaEntitiesIds.get(accId).isEmpty()) {
			LOG.debug("Cant remove last player bestia entity.");
			return false;
		}

		entityService.delete(playerBestia);
		playerBestiaEntitiesIds.remove(accId, playerBestia.getId());

		if (activeEntities.get(accId) == playerBestia.getId()) {
			// Select a new active bestia and notify the client.
			long newActive = playerBestiaEntitiesIds.get(accId).stream().findAny().orElse(0L);

			if (newActive == 0) {
				LOG.warn("Could not select a new active bestia for account {}", accId);
				return false;
			}

			setActiveEntity(accId, newActive);
			return true;
		}

		return true;
	}
}
