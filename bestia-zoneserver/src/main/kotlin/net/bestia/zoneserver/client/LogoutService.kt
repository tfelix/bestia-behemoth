package net.bestia.zoneserver.client

import mu.KotlinLogging
import net.bestia.entity.EntityService
import net.bestia.messages.MessageApi
import net.bestia.messages.client.ToClientEnvelope
import net.bestia.messages.login.LogoutMessage
import net.bestia.messages.login.LogoutState
import net.bestia.model.dao.AccountDAO
import net.bestia.model.dao.findOneOrThrow
import net.bestia.model.domain.Account
import net.bestia.zoneserver.entity.PlayerEntityService
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger {  }

@Service
class LogoutService(
        private val accountDao: AccountDAO,
        private val akkaApi: MessageApi,
        private val playerEntityService: PlayerEntityService,
        private val entityService: EntityService,
				private val connectionService: ConnectionService
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

		val acc = accountDao.findOneOrThrow(accId)

		if (acc == null) {
			LOG.warn("Can not logout account id: {}. ID does not exist.", accId)
			return
		}

		// Send disconnect message to the webserver.
		// Depending on the logout state the actor might have already been
		// stopped.
		val logoutMsg = LogoutMessage(LogoutState.NO_REASON)
    akkaApi.send(ToClientEnvelope(acc.id, logoutMsg))

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

    connectionService.removeConnection(accId)
	}

	/**
	 * Perform a logout on all users currently connected to the server.
	 */
	fun logoutAllUsers() {
    connectionService.iterateOverConnections({
      logout(it)
    })
	}

	/**
	 * Logs out all users who are blow the given user level.
	 *
	 * @param level
	 */
	fun logoutAllUsersBelow(level: Account.Companion.UserLevel) {
    connectionService.iterateOverConnections({
      logout(it)
    }, {
      val account = accountDao.findOneOrThrow(it)
      account.userLevel < level
    })
	}
}
