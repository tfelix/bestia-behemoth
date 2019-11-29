package net.bestia.zoneserver.entity

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOne
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.component.*
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * This service manages the entities which are controlled by a player.
 *
 * @author Thomas Felix
 */
@Service
class PlayerEntityService(
    private val playerBestiaDao: PlayerBestiaRepository
) {

  /**
   * Returns the active entity id for the given account.
   *
   * @param accountId The account id.
   * @return The active entity id of this account or null.
   */
  fun getActivePlayerEntityId(accountId: Long): Long? {
    return null
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
   * Deletes all player bestias for this given account id from the system.
   *
   * @param accId The account id to delete all bestias from.
   */
  fun removeEntityIdsFromAccount(accountId: Long) {
    LOG.trace { "removeEntityIdsFromAccount(): For account $accountId." }

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
    val playerBestia = playerBestiaDao.findOneOrThrow(playerComp.playerBestiaId)

    // Current status values (HP/Mana)
    val conditionComp = playerEntity.getComponent(ConditionComponent::class.java)
    playerBestia.conditionValues = conditionComp.conditionValues

    // Current position.
    val posComp = playerEntity.getComponent(PositionComponent::class.java)
    playerBestia.currentPosition = posComp.position

    // Level and exp.
    val levelComp = playerEntity.getComponent(LevelComponent::class.java)
    playerBestia.exp = levelComp.exp
    playerBestia.level = levelComp.level

    playerBestiaDao.save(playerBestia)
  }
}
