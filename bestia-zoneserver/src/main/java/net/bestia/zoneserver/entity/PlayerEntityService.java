package net.bestia.zoneserver.entity;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IMap;
import com.hazelcast.core.MultiMap;
import net.bestia.entity.Entity;
import net.bestia.entity.EntityService;
import net.bestia.entity.component.LevelComponent;
import net.bestia.entity.component.PlayerComponent;
import net.bestia.entity.component.PositionComponent;
import net.bestia.entity.component.StatusComponent;
import net.bestia.model.domain.PlayerBestia;
import net.bestia.model.geometry.Rect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * This service manages the entities which are controlled by a player.
 *
 * @author Thomas Felix
 */
@Service
public class PlayerEntityService {

  private final static Logger LOG = LoggerFactory.getLogger(PlayerEntityService.class);

  private final static String ACTIVE_ENTITIES_KEY = "entities.player.active";
  private final static String PLAYER_ENTITIES_KEY = "entities.player";

  private final MultiMap<Long, Long> playerBestiaEntitiesIds;
  private final IMap<Long, Long> activeEntities;
  private final EntityService entityService;
  private final EntitySearchService entitySearchService;
  private final PlayerBestiaService playerBestiaService;

  @Autowired
  public PlayerEntityService(HazelcastInstance hz,
                             EntityService entityService,
                             EntitySearchService entitySearchService,
                             PlayerBestiaService playerBestiaService) {

    this.activeEntities = hz.getMap(ACTIVE_ENTITIES_KEY);
    this.playerBestiaEntitiesIds = hz.getMultiMap(PLAYER_ENTITIES_KEY);
    this.entityService = Objects.requireNonNull(entityService);
    this.entitySearchService = Objects.requireNonNull(entitySearchService);
    this.playerBestiaService = Objects.requireNonNull(playerBestiaService);
  }

  /**
   * Sets the entity id as the active player bestia for the given account id.
   * This will throw if the given entity id does not exist in the system.
   *
   * @param accId
   * @param activeEntityId
   * @throws IllegalArgumentException If the entity id does not exist.
   */
  public void setActiveEntity(long accId, long activeEntityId) {
    // Check if this is a valid player bestia.
    final Entity activeEntity = entityService.getEntity(activeEntityId);

    if (null == activeEntity) {
      throw new IllegalArgumentException("Active entity id was not found in the system. Add it first.");
    }

    final PlayerComponent playerComp = entityService.getComponent(activeEntity, PlayerComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    // Safety check if the player owns this entity.
    if (playerComp.getOwnerAccountId() != accId) {
      throw new IllegalArgumentException("Account ID does not own entity id. Can not activate.");
    }

    // Remove the active flag from the last active player bestia.
    activeEntities.put(accId, activeEntityId);

    LOG.debug("Activating entity id: {} for account: {}.", activeEntityId, accId);
  }

  /**
   * Checks if the given entity id is the current active entity of the
   * account.
   *
   * @param entityId The entity which should be checked if its active.
   * @return TRUE if this is the active entity. FALSE otherwise.
   */
  private boolean isActiveEntity(long entityId) {

    final long accId = entityService.getComponent(entityId, PlayerComponent.class)
            .map(PlayerComponent::getOwnerAccountId)
            .orElse(0L);

    if (accId == 0) {
      return false;
    }

    final Long active = activeEntities.get(accId);
    final boolean isActive = active != null && active == entityId;

    LOG.trace("Entity {} of account {} is active: {}.", entityId, accId, isActive);

    return isActive;
  }

  /**
   * Returns the active player bestia entity for the given account it.
   *
   * @param accId The account id.
   * @return The active {@link Entity} of this account or null.
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
   * Tries to find the active player bestia id of the current selected bestia.
   *
   * @return NULL if this account has no active bestia. The id otherwise.
   */
  public long getActivePlayerBestiaId(long accId) {
    final Entity e = getActivePlayerEntity(accId);
    if (e == null) {
      return 0;
    }

    return entityService.getComponent(e, PlayerComponent.class)
            .map(PlayerComponent::getPlayerBestiaId)
            .orElse(0L);
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

    final Set<Entity> entitiesInRange = entitySearchService.getCollidingEntities(range);

    LOG.trace("Entities in range: {}", entitiesInRange);

    // Filter only for active entities.
    final List<Long> activeAccountIds = entitiesInRange.stream()
            .filter(entity -> {
              return isActiveEntity(entity.getId());
            })
            .map(entity -> {
              return entityService.getComponent(entity, PlayerComponent.class)
                      .map(PlayerComponent::getOwnerAccountId)
                      .orElse(0L);
            })
            .filter(id -> id != 0)
            .collect(Collectors.toList());

    LOG.trace("Active player entities {} in range: {}", activeAccountIds, range);

    return activeAccountIds;
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
   * @param accId The account id to lookup the master.
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
   * <p>
   * This registers the given entities as player bestias. Only entities owning
   * the component {@link PlayerComponent} will be processed by this call.
   *
   * @param pb A collection of player bestias.
   */
  public void putPlayerEntities(Collection<Entity> pb) {
    pb.forEach(this::putPlayerEntity);
  }

  /**
   * Checks if the given account owns this entity.
   *
   * @param accId    An account id.
   * @param entityId The entity for which ownership should be checked.
   * @return TRUE if the player owns this entity. FALSE if not or the
   * account/entity was not found.
   */
  public boolean hasPlayerEntity(long accId, long entityId) {
    return playerBestiaEntitiesIds.containsEntry(accId, entityId);
  }

  /**
   * Puts a single {@link Entity} into the cache.
   *
   * @param entity The player entity to put into the cache.
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
   * @param accId The account id to delete all bestias from.
   */
  public void removePlayerBestias(long accId) {
    LOG.trace("removePlayerBestias(): {}.", accId);

    playerBestiaEntitiesIds.remove(accId);

    // Remove the active bestia if it was set.
    activeEntities.remove(accId);
  }

  /**
   * Removes the given entity (player bestia) from the active system.
   *
   * @param playerBestia The player bestia entity to be removed.
   */
  public void removePlayerBestia(Entity playerBestia) {

    Objects.requireNonNull(playerBestia);

    final PlayerComponent playerComp = entityService.getComponent(playerBestia, PlayerComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    final long accId = playerComp.getOwnerAccountId();

    // Dont remove if its the last bestia.
    if (playerBestiaEntitiesIds.get(accId).isEmpty()) {
      LOG.debug("Cant remove last player bestia entity.");
      return;
    }

    entityService.delete(playerBestia);
    playerBestiaEntitiesIds.remove(accId, playerBestia.getId());

    if (!activeEntities.containsKey(accId)) {
      return;
    }

    if (activeEntities.get(accId) == playerBestia.getId()) {
      // Select a new active bestia and notify the client.
      long newActive = playerBestiaEntitiesIds.get(accId).stream().findAny().orElse(0L);

      if (newActive == 0) {
        LOG.warn("Could not select a new active bestia for account {}", accId);
      }

      setActiveEntity(accId, newActive);
    }

  }

  /**
   * This method extracts all variable and important data from the player
   * entity and persists them back into the database.
   */
  public void save(Entity playerEntity) {

    final PlayerComponent playerComp = entityService.getComponent(playerEntity, PlayerComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    // Get the player bestia.
    final PlayerBestia playerBestia = playerBestiaService.getPlayerBestia(playerComp.getPlayerBestiaId());

    // Current status values (HP/Mana)
    final StatusComponent statusComp = entityService.getComponent(playerEntity, StatusComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    playerBestia.setStatusValues(statusComp.getConditionValues());

    // Current position.
    final PositionComponent posComp = entityService.getComponent(playerEntity, PositionComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    playerBestia.setCurrentPosition(posComp.getPosition());

    // Level and exp.
    final LevelComponent levelComp = entityService.getComponent(playerEntity, LevelComponent.class)
            .orElseThrow(IllegalArgumentException::new);

    playerBestia.setExp(levelComp.getExp());
    playerBestia.setLevel(levelComp.getLevel());

    // Current inventory.

    playerBestiaService.save(playerBestia);
  }
}
