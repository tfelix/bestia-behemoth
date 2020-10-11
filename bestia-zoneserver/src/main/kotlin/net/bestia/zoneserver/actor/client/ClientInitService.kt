package net.bestia.zoneserver.actor.client

import mu.KotlinLogging
import net.bestia.model.account.AccountRepository
import net.bestia.model.findOneOrThrow
import net.bestia.zoneserver.entity.Entity
import net.bestia.zoneserver.entity.factory.PlayerBestiaFactory
import org.springframework.stereotype.Service
import javax.transaction.Transactional

private val LOG = KotlinLogging.logger { }

sealed class InitResult

data class InitResultExistingEntity(
    val activeEntityId: Long
) : InitResult()

data class InitResultNewEntity(
    val spawnedActiveEntity: Entity
) : InitResult()

@Service
@Transactional
class ClientInitService(
    private val accountRepository: AccountRepository,
    private val playerBestiaFactory: PlayerBestiaFactory
) {

  fun setupDefaultActivePlayerBestia(accountId: Long): InitResult {
    LOG.trace { "Setting default active Player Bestia for account $accountId" }

    val account = accountRepository.findOneOrThrow(accountId)
    val master = account.masterBestia

    requireNotNull(master) {
      "Player has not Master yet setup. Create a master first"
    }

    account.activeBestia = master

    val result = if (master.entityId == 0L) {
      LOG.debug { "Account $accountId has no active master entity, spawning it" }
      val entity = playerBestiaFactory.build(master.id)
      master.entityId = entity.id

      InitResultNewEntity(entity)
    } else {
      InitResultExistingEntity(master.entityId)
    }

    accountRepository.save(account)

    return result
  }
}