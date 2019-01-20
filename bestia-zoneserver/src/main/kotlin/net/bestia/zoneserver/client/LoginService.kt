package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.model.account.Account
import net.bestia.zoneserver.entity.PlayerBestiaService
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.entity.factory.EntityFactory
import net.bestia.zoneserver.entity.factory.PlayerBestiaBlueprint
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger {  }

/**
 * Performs login of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginService(
    private val accountDao: AccountRepository,
    private val playerEntityService: PlayerEntityService,
    private val entityFactory: EntityFactory,
    private val playerBestiaService: PlayerBestiaService
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
    val master = playerBestiaService.getMaster(accId)

    LOG.debug { "Login of account: $account" }

    val playerBestiaBlueprint = PlayerBestiaBlueprint(master.id)
    val masterEntity = entityFactory.build(playerBestiaBlueprint)

    playerEntityService.updatePlayerBestiaWithEntityId(masterEntity)
    playerEntityService.setActiveEntity(accId, masterEntity)

    master.entityId = masterEntity.id
    playerBestiaService.save(master)

    return account
  }
}