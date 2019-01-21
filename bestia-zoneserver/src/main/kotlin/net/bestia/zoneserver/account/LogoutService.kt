package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.zoneserver.entity.EntityService
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.SaveAndKillEntity
import net.bestia.messages.login.LogoutMessage
import net.bestia.messages.login.LoginResponse
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOne
import net.bestia.model.account.AccountType
import net.bestia.zoneserver.MessageApi
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Service
class LogoutService(
    private val accountDao: AccountRepository,
    private val messageApi: MessageApi,
    private val playerEntityService: PlayerEntityService,
    private val entityService: EntityService
) {

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

    val acc = accountDao.findOne(accId) ?: return

    // Send disconnect message to the webserver.
    // Depending on the logout state the actor might have already been
    // stopped.
    val logoutMsg = LogoutMessage(LoginResponse.NO_REASON)
    messageApi.send(ClientEnvelope(acc.id, logoutMsg))

    val playerEntities = playerEntityService.getPlayerEntities(accId)
    playerEntities.forEach { entityId ->
      messageApi.send(EntityEnvelope(entityId, SaveAndKillEntity))
    }

    playerEntityService.removeEntityIdsFromAccount(accId)

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
  fun logoutAllUsers() {
    throw IllegalStateException("Must be implemented")
  }

  /**
   * Logs out all users who are blow the given user level.
   *
   * @param level
   */
  fun logoutAllUsersBelow(level: AccountType) {
    throw IllegalStateException("Must be implemented")
  }
}
