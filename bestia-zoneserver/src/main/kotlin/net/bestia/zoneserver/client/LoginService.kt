package net.bestia.zoneserver.client


import net.bestia.entity.Entity
import net.bestia.entity.EntityService
import net.bestia.entity.component.PlayerComponent
import net.bestia.entity.factory.PlayerBestiaEntityFactory
import net.bestia.messages.MessageApi
import net.bestia.messages.account.AccountLoginRequest
import net.bestia.messages.login.LogoutMessage
import net.bestia.model.dao.AccountDAO
import net.bestia.model.domain.Account
import net.bestia.model.domain.Account.UserLevel
import net.bestia.model.server.MaintenanceLevel
import net.bestia.zoneserver.configuration.RuntimeConfigService
import net.bestia.zoneserver.entity.PlayerBestiaService
import net.bestia.zoneserver.entity.PlayerEntityService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*

/**
 * Performs login or logout of the bestia server system.
 *
 * @author Thomas Felix
 */
@Service
class LoginService @Autowired
constructor(config: RuntimeConfigService,
            accountDao: AccountDAO,
            playerEntityService: PlayerEntityService,
            akkaApi: MessageApi,
            playerEntityFactory: PlayerBestiaEntityFactory,
            playerBestiaService: PlayerBestiaService,
            playerFactory: PlayerBestiaEntityFactory,
            entityService: EntityService) {

  private val config: RuntimeConfigService
  private val accountDao: AccountDAO
  private val playerEntityService: PlayerEntityService
  private val playerBestiaService: PlayerBestiaService
  private val akkaApi: MessageApi
  private val entityService: EntityService
  private val playerEntityFactory: PlayerBestiaEntityFactory

  init {

    this.config = Objects.requireNonNull(config)
    this.accountDao = Objects.requireNonNull(accountDao)
    this.playerEntityService = Objects.requireNonNull(playerEntityService)
    this.akkaApi = Objects.requireNonNull(akkaApi)
    this.entityService = Objects.requireNonNull(entityService)
    this.playerBestiaService = Objects.requireNonNull(playerBestiaService)
    this.playerEntityFactory = Objects.requireNonNull(playerEntityFactory)
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

    return account
  }

  /**
   * Logouts a player. Also cleans up all the data and persists it back to the
   * database.
   *
   * @param accId
   * The account id to logout.
   */
  fun logout(accId: Long) {
    if (accId <= 0) {
      throw IllegalArgumentException("Account ID must be positive.")
    }
    // Unregister connection.
    LOG.debug("Logout account: {}.", accId)

    val acc = accountDao.findOne(accId)

    if (acc == null) {
      LOG.warn("Can not logout account id: {}. ID does not exist.", accId)
      return
    }

    // Send disconnect message to the webserver.
    // Depending on the logout state the actor might have already been
    // stopped.
    val logoutMsg = LogoutMessage(accId)
    akkaApi.sendToClient(accId, logoutMsg)

    val playerEntities = playerEntityService.getPlayerEntities(accId)

    playerEntities.forEach { entity ->
      try {
        playerEntityService.save(entity)
      } catch (e: Exception) {
        LOG.warn("Something went wrong saving entity {}.", entity, e)
      }
    }

    // Only remove the player bestia.
    playerEntityService.removePlayerBestias(accId)

    LOG.trace("Removing player bestias.")

    // Recycle all entities.
    playerEntities.forEach { entity ->
      try {
        entityService.delete(entity)
      } catch (e: Exception) {
        LOG.warn("Something went wrong deleting entity {}.", entity, e)
      }
    }
  }

  /**
   * Perform a logout on all users currently connected to the server.
   */
  fun logoutAll() {
    // TODO Auto-generated method stub

  }

  /**
   * Logs out all users who are blow the given user level.
   *
   * @param level
   */
  fun logoutAllUsersBelow(level: UserLevel) {
    throw IllegalStateException("Currently broken.")
    /*
     * connectionService.getAllConnectedAccountIds().forEachRemaining(accId
     * -> { final Account acc = accountDao.findOne(accId);
     *
     * if (acc.getUserLevel().compareTo(level) == -1) { logout(accId); } });
     */
  }

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

  companion object {

    private val LOG = LoggerFactory.getLogger(LoginService::class.java)
  }
}
