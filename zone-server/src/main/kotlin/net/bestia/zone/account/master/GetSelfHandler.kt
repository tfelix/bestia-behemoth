package net.bestia.zone.account.master

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.session.ConnectionInfoService
import net.bestia.zone.message.GetSelfCMSG
import net.bestia.zone.message.SelfSMSG
import net.bestia.zone.message.processor.InMessageProcessor
import net.bestia.zone.message.processor.OutMessageProcessor
import net.bestia.zone.util.AccountId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional


@Component
class GetSelfHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaInfoFactory: BestiaInfoFactory
) : InMessageProcessor.IncomingMessageHandler<GetSelfCMSG> {
  override val handles = GetSelfCMSG::class

  @Transactional(readOnly = true)
  override fun handle(msg: GetSelfCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val selfInfo = getSelfInfo(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, selfInfo)

    return true
  }

  private fun getSelfInfo(accountId: AccountId): SelfSMSG {
    val masterId = connectionInfoService.getMasterId(accountId)
    val selectedMasterEntityId = connectionInfoService.getSelectedMasterEntityId(accountId)
    val bestiaEntities = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)

    // Fetch all player bestias for this master in one query
    val bestiaInfos = bestiaInfoFactory.getBestiaInfo(bestiaEntities)

    return SelfSMSG(
      masterId = masterId,
      masterEntityId = selectedMasterEntityId,
      availableBestias = bestiaInfos
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
