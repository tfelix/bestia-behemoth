package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.PlayerComponent
import net.bestia.entity.factory.PlayerBestiaEntityFactory
import net.bestia.messages.account.AccountLoginRequest
import net.bestia.model.dao.AccountDAO
import net.bestia.model.domain.Account
import net.bestia.model.domain.Account.UserLevel
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.configuration.RuntimeConfigService
import net.bestia.zoneserver.entity.PlayerBestiaService
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.stereotype.Service
import java.util.*

private val LOG = KotlinLogging.logger {  }

/**
 * Performs login of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginService(
        private val config: RuntimeConfigService,
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

    val account = accountDao.findOne(accId)

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
    playerEntityService.putPlayerEntity(masterEntity)
    playerEntityService.setActiveEntity(accId, masterEntity!!.id)

    // Update the DB.
    master.entityId = masterEntity.id
    playerBestiaService.save(master)

    connectionService.addConnection(accId)

    return account
  }

  @Deprecated("User are now directly logged in via web")
  fun setNewLoginToken(request: AccountLoginRequest): AccountLoginRequest {
    Objects.requireNonNull(request)

    LOG.debug("Trying to set login token for username {}.", request)

    val account = accountDao.findByUsernameOrEmail(request.username)

    if (account == null) {
      LOG.debug("Account with username {} not found.", request.username)
      return request.fail()
    }

    if (!account.password.matches(request.password)) {
      LOG.debug("Password does not match: {}.", request)
      return request.fail()
    }

    // Create new token and save it.
    val uuid = UUID.randomUUID().toString()
    account.loginToken = uuid
    accountDao.save(account)

    // Check login.
    return request.success(account.id, uuid)
  }

  /**
   * An user can only login if he provides the correct login token and the
   * server is not in maintenance mode. A game master can override the server
   * maintenance mode flag.
   *
   * @param accId
   * The account ID to check if the login.
   * @param token
   * The token of this account id.
   * @return TRUE if the account is permitted to login FALSE otherwise.
   */
  @Deprecated("User are now directly logged in via web")
  fun canLogin(accId: Long, token: String): Boolean {
    Objects.requireNonNull(token)

    LOG.debug("Checking login for account {}.", accId)

    val acc = accountDao.findOne(accId)

    if (acc == null) {
      LOG.trace("No account with id {} found.", accId)
      return false
    }

    if (acc.loginToken.isEmpty()) {
      LOG.debug("Login with empty token is not allowed.")
      return false
    }

    if (acc.loginToken != token) {
      LOG.trace("Account {} logintoken does not match.", accId)
      return false
    }

    // Special handling of maintenance mode.
    if (config.maintenanceMode != MaintenanceLevel.NONE) {

      // Depending on maintenance mode certain users can login.
      if (config.maintenanceMode == MaintenanceLevel.FULL) {
        LOG.debug("No accounts can login during full maintenance.")
        return false
      }

      if (config.maintenanceMode == MaintenanceLevel.PARTIAL && acc.userLevel.compareTo(UserLevel.SUPER_GM) < 0) {
        LOG.debug("Account {} can not login during maintenance User level too low.", accId)
        return false
      }
    }
    LOG.trace("Account {} login permitted.", accId)
    return true
  }
}