package net.bestia.zoneserver.account

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.bestia.PlayerBestiaRepository
import net.bestia.model.findOneOrThrow
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
    private val playerBestiaFactory: PlayerBestiaFactory,
    private val accountRepository: AccountRepository
) {

  fun setup(accId: Long): PlayerEntitySetupResult {
    require(accId >= 0) { "Account ID must be positive." }

    val account = accountRepository.findOneOrThrow(accId)
    val master = account.masterBestia

    requireNotNull(master) {
      "Account $accId has no master assigned"
    }

    val masterEntity = playerBestiaFactory.build(master.id)

    LOG.debug { "Adding player entity: accId: $accId, entityId: ${master.entityId}" }

    master.entityId = masterEntity.id
    playerBestiaRepository.save(master)

    // FIXME Also build/discover the player bestia

    return PlayerEntitySetupResult(
        masterEntityId = master.entityId,
        playerBestiaIds = emptyList()
    )
  }
}