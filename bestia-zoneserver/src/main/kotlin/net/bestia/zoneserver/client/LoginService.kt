package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.EntityService
import net.bestia.zoneserver.entity.component.PlayerComponent
import net.bestia.zoneserver.entity.factory.PlayerBestiaEntityFactory
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.Account
import net.bestia.zoneserver.entity.PlayerBestiaService
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger {  }

/**
 * Performs login of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginService(
    private val accountDao: AccountDAO,
    private val playerEntityService: PlayerEntityService,
    private val playerEntityFactory: PlayerBestiaEntityFactory,
    private val playerBestiaService: PlayerBestiaService,
    private val entityService: EntityService,
    private val connectionService: ConnectionService
) {

  /**
   * Performs a login for this account. This prepares the bestia server system
   * for upcoming commands from this player. The player bestia entity is
   * spawned on the server.
   *
   * @param accId
   * The account id to perform a login.
   * @return The logged in Account or NULL if login failed.
   */
  fun login(accId: Long): Account? {
    if (accId < 0) {
      throw IllegalArgumentException("Account ID must be positive.")
    }

    val account = accountDao.findOneOrThrow(accId)

    if (account == null) {
      LOG.warn("Account {} was not found.", accId)
      return null
    }

    // Spawn the player bestia of this account.
    val master = playerBestiaService.getMaster(accId)

    var masterEntity: Entity? = null

    if (master!!.entityId != 0L) {
      LOG.debug("Login in acc: {}. Master bestia already spawned (eid: {}). Using it.", accId,
              master.entityId)

      masterEntity = entityService.getEntity(master.entityId)

      // Safetycheck the entity.
      val isPlayersMasterEntity = entityService.getComponent(masterEntity, PlayerComponent::class.java)
              .map { pc -> pc.playerBestiaId == master.id }
              .orElse(false)

      if (masterEntity == null || !isPlayersMasterEntity) {
        LOG.warn("Master entity {} for account {} not found even though ID was set. Spawning it.",
                master.entityId, accId)
        masterEntity = playerEntityFactory.build(master)
      }

    } else {
      LOG.debug("Login in acc: {}. Spawning master bestias.", accId)
      masterEntity = playerEntityFactory.build(master)
    }

    // Save the entity.
    // Now activate the master and notify the client.
    playerEntityService.updatePlayerBestiaWithEntityId(masterEntity)
    playerEntityService.setActiveEntity(accId, masterEntity!!.id)

    // Update the DB.
    master.entityId = masterEntity.id
    playerBestiaService.save(master)

    connectionService.addConnection(accId)

    return account
  }
}