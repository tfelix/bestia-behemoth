package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.PlayerBestiaDAO
import net.bestia.model.dao.findOne
import net.bestia.model.dao.findOneOrThrow
import net.bestia.zoneserver.entity.component.LevelComponent
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.component.PositionComponent
import net.bestia.zoneserver.entity.component.StatusComponent
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This service manages the entities which are controlled by a player.
 *
 * @author Thomas Felix
 */
@Service
class PlayerEntityService(
    private val playerBestiaService: PlayerBestiaService,
    private val accountDao: AccountDAO,
    private val playerBestiaDao: PlayerBestiaDAO
) {

  /**
   * Sets the entity id as the active player bestia for the given account id.
   * This will throw if the given entity id does not exist in the system.
   *
   * @throws IllegalArgumentException If the entity does not belong to the given account id.
   */
  fun setActiveEntity(accId: Long, entity: Entity) {

    val playerComp = entity.getComponent(PlayerComponent::class.java)
    if (playerComp.ownerAccountId != accId) {
      throw IllegalArgumentException("Account ID does not own entity id. Can not activate.")
    }

    val account = accountDao.findOneOrThrow(accId)
    account.activeBestiaEntityId = entity.id
    accountDao.save(account)

    LOG.debug { "Activating entity id: ${entity.id} for account: $accId." }
  }

  /**
   * Checks if the given entity id is the current active entity of the
   * account.
   *
   * @param entityId The entity which should be checked if its active.
   * @return TRUE if this is the active entity. FALSE otherwise.
   */
  private fun isActiveEntity(accountId: Long, entityId: Long): Boolean {
    val account = accountDao.findOne(accountId) ?: return false

    return account.activeBestiaEntityId == entityId
  }

  /**
   * Returns the active entity id for the given account.
   *
   * @param accountId The account id.
   * @return The active entity id of this account or null.
   */
  fun getActivePlayerEntityId(accountId: Long): Long? {
    val account = accountDao.findOne(accountId) ?: return null

    return account.activeBestiaEntityId
  }

  /**
   * Returns all player bestia entity ids for a given account.
   */
  fun getPlayerEntities(accountId: Long): Set<Long> {
    return playerBestiaDao.findPlayerBestiasForAccount(accountId)
        .asSequence()
        .map { it.entityId }
        .toSet()
  }

  /**
   * Inserts the given player bestias into the cache. The player bestias are
   * not required be from the same player account. This will be taken care
   * off.
   *
   *
   * This registers the given entities as player bestias. Only entities owning
   * the component [PlayerComponent] will be processed by this call.
   *
   * @param pb A collection of player bestias.
   */
  fun updatePlayerBestiaWithEntityId(playerBestias: Collection<Entity>) {
    playerBestias.forEach { updatePlayerBestiaWithEntityId(it) }
  }

  /**
   * Puts a single [Entity] into the cache.
   *
   * @param entity The player entity to put into the cache.
   */
  fun updatePlayerBestiaWithEntityId(entity: Entity) {
    // Can only add entities with player component.
    val playerComponent = entity.getComponent(PlayerComponent::class.java)
    val accId = playerComponent.ownerAccountId
    val entityId = entity.id

    LOG.debug("Adding player entity: accId: {}, entityId: {}.", accId, entityId)

    val playerBestia = playerBestiaDao.findOneOrThrow(playerComponent.playerBestiaId)
    playerBestia.entityId = entityId
    playerBestiaDao.save(playerBestia)
  }

  /**
   * Checks if the given account owns this entity.
   *
   * @param accId    An account id.
   * @param entityId The entity for which ownership should be checked.
   * @return TRUE if the player owns this entity. FALSE if not or the
   * account/entity was not found.
   */
  fun hasPlayerEntity(accId: Long, entityId: Long): Boolean {
    return playerBestiaDao.findPlayerBestiasForAccount(accId).any { it.entityId == entityId }
  }

  /**
   * Deletes all player bestias for this given account id from the system.
   *
   * @param accId The account id to delete all bestias from.
   */
  fun removeEntityIdsFromAccount(accountId: Long) {
    LOG.trace { "removeEntityIdsFromAccount(): For account $accountId." }

    accountDao.findOneOrThrow(accountId)?.let {
      it.activeBestiaEntityId = 0
      accountDao.save(it)
    }

    val updatedPlayerBestias = playerBestiaDao.findPlayerBestiasForAccount(accountId)
        .map {
          it.entityId = 0
          it
        }
    playerBestiaDao.saveAll(updatedPlayerBestias)
  }

  /**
   * This method extracts all variable and important data from the player
   * entity and persists them back into the database.
   */
  fun save(playerEntity: Entity) {
    val playerComp = playerEntity.getComponent(PlayerComponent::class.java)
    val playerBestia = playerBestiaService.getPlayerBestia(playerComp.playerBestiaId)

    // Current status values (HP/Mana)
    val statusComp = playerEntity.getComponent(StatusComponent::class.java)
    playerBestia.conditionValues = statusComp.conditionValues

    // Current position.
    val posComp = playerEntity.getComponent(PositionComponent::class.java)
    playerBestia.currentPosition = posComp.position

    // Level and exp.
    val levelComp = playerEntity.getComponent(LevelComponent::class.java)
    playerBestia.exp = levelComp.exp
    playerBestia.level = levelComp.level

    playerBestiaService.save(playerBestia)
  }
}
