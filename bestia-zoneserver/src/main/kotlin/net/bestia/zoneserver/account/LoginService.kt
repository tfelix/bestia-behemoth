package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.Account
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.AccountType
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOne
import net.bestia.model.findOneOrThrow
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.config.RuntimeConfigService
import net.bestia.zoneserver.entity.PlayerEntityService
import net.bestia.zoneserver.entity.factory.PlayerBestiaFactory
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Performs login of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginService(
    private val accountRepository: AccountRepository,
    private val playerBestiaRepository: PlayerBestiaRepository,
    private val playerEntityService: PlayerEntityService,
    private val playerBestiaFactory: PlayerBestiaFactory,
    private val runtimeConfigService: RuntimeConfigService
) {

  fun isLoginAllowedForAccount(accountId: Long): Boolean {
    val currentMaintenanceLevel = runtimeConfigService.getRuntimeConfig().maintenanceLevel
    val account = accountRepository.findOne(accountId)
        ?: return false

    return when (currentMaintenanceLevel) {
      MaintenanceLevel.NONE -> true
      MaintenanceLevel.PARTIAL -> account.userLevel > AccountType.GM
      MaintenanceLevel.FULL -> false
    }
  }

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

    val account = accountRepository.findOneOrThrow(accId)
    val master = playerBestiaRepository.findMasterBestiaForAccount(accId)
        ?: throw IllegalArgumentException("Account had no BestiaMaster assigned")

    LOG.debug { "Login of account: $account" }

    val masterEntity = playerBestiaFactory.build(master.id)
    playerEntityService.updatePlayerBestiaWithEntityId(masterEntity)
    playerEntityService.setActiveEntity(accId, masterEntity)

    master.entityId = masterEntity.id
    playerBestiaRepository.save(master)
    return account
  }
}