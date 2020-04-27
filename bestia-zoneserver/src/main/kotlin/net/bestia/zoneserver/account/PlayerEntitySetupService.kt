package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.zoneserver.entity.factory.PlayerBestiaFactory
import org.springframework.stereotype.Service

private val LOG = KotlinLogging.logger { }

/**
 * Prepares and setup all player entities so the player can access them after wards.
 *
 * TODO Check if this service is justified with only one method.
 */
@Service
class PlayerEntitySetupService(
    private val playerBestiaRepository: PlayerBestiaRepository,
    private val playerBestiaFactory: PlayerBestiaFactory
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
  fun setup(accId: Long) {
    require(accId >= 0) { "Account ID must be positive." }

    val master = playerBestiaRepository.findMasterBestiaForAccount(accId)
        ?: throw IllegalArgumentException("Account $accId has no master assigned")

    val masterEntity = playerBestiaFactory.build(master.id)

    LOG.debug { "Adding player entity: accId: $accId, entityId: masterEntity.id" }

    master.entityId = masterEntity.id
    playerBestiaRepository.save(master)
  }
}