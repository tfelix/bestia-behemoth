package net.bestia.zone.bestia

import net.bestia.zone.account.master.BestiaInfoFactory
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.core.session.NoActiveSessionException
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component

/**
 * Tells a player which bestia they own now. Reads the database, so never call it on the tick thread.
 */
@Component
class OwnedBestiasPublisher(
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaInfoFactory: BestiaInfoFactory,
  private val outMessageProcessor: OutMessageProcessor,
) {

  fun publish(accountId: AccountId) {
    val masterId = try {
      connectionInfoService.getMasterId(accountId)
    } catch (_: NoActiveSessionException) {
      // Offline: the list goes out with the next master selection instead.
      return
    }

    val owned = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
    val infos = bestiaInfoFactory.getBestiaInfo(owned)

    outMessageProcessor.sendToPlayer(accountId, OwnedBestiasSMSG(infos))
  }
}
