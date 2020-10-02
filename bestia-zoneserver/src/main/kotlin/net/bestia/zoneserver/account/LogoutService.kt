package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.messages.client.ClientEnvelope
import net.bestia.zoneserver.actor.entity.EntityEnvelope
import net.bestia.zoneserver.actor.entity.commands.SaveAndKillEntityCommand
import net.bestia.model.account.AccountRepository
import net.bestia.model.account.AccountType
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.actor.routing.MessageApi
import net.bestia.zoneserver.actor.socket.LogoutMessage
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import java.lang.IllegalStateException

private val LOG = KotlinLogging.logger { }

@Service
class LogoutService(
    private val accountDao: AccountRepository,
    private val messageApi: MessageApi,
    private val playerBestiaRepository: PlayerBestiaRepository
) {

  /**
   * Logouts a player. Also cleans up all the data and persists it back to the
   * database.
   *
   * @param accId
   * The account id to logout.
   */
  fun logout(accId: Long) {
    require(accId > 0) { "Account ID must be positive." }

    // Unregister connection.
    LOG.debug("Logout account: {}.", accId)

    val acc = accountDao.findByIdOrNull(accId) ?: return

    // Send disconnect message to the webserver.
    // Depending on the logout state the actor might have already been
    // stopped.
    messageApi.send(ClientEnvelope(acc.id, LogoutMessage))

    val playerEntities = getPlayerEntities(accId)
    playerEntities.forEach { entityId ->
      messageApi.send(EntityEnvelope(entityId, SaveAndKillEntityCommand))
    }
  }

  /**
   * Returns all player bestia entity ids for a given account.
   */
  private fun getPlayerEntities(accountId: Long): Set<Long> {
    return playerBestiaRepository.findPlayerBestiasForAccount(accountId)
        .map { it.entityId }
        .toSet()
  }

  /**
   * Deletes all player bestias for this given account id from the system.
   *
   * @param accId The account id to delete all bestias from.
   */
  private fun removeEntityIdsFromAccount(accountId: Long) {
    LOG.trace { "removeEntityIdsFromAccount(): For account $accountId." }

    val updatedPlayerBestias = playerBestiaRepository.findPlayerBestiasForAccount(accountId)
        .map {
          it.entityId = 0
          it
        }
    playerBestiaRepository.saveAll(updatedPlayerBestias)
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
