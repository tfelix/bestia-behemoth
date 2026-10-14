package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.master.OwnerComponentResync
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.aoi.EntityVisibility
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.SelfSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import net.bestia.zone.master.BestiaInfoFactory
import net.bestia.zone.account.AvailableBestiaSlotService


@Component
class GetSelfHandler(
  private val outMessageProcessor: OutMessageProcessor,
  private val connectionInfoService: ConnectionInfoService,
  private val bestiaInfoFactory: BestiaInfoFactory,
  private val bestiaSlots: AvailableBestiaSlotService,
  private val world: WorldView,
  private val entityVisibility: EntityVisibility
) : IoMessageHandler<GetSelfCMSG> {
  override val wire = decoder(MessageCase.GET_SELF) { accountId, _ -> GetSelfCMSG(accountId) }

  /**
   * Note this runs `readOnly = true` and also briefly waits for a world scope inside that transaction
   * (at most one tick). All the DB reads happen in [getSelfInfo] first, so nothing does I/O inside the
   * scope; `world.post` is the async alternative if that ever becomes a problem.
   */
  @Transactional(readOnly = true)
  override fun handle(msg: GetSelfCMSG): Boolean {
    val selfInfo = getSelfInfo(msg.playerId)

    outMessageProcessor.sendToPlayer(msg.playerId, selfInfo)

    // Strictly after the send: the components below reach the client on the *next* tick, and the
    // client's UI drops any entity message that arrives before it has learned its master entity id
    // from this SelfSMSG.
    resyncOwnerComponents(selfInfo.masterEntityId)
    entityVisibility.reannounce(msg.playerId)

    return true
  }

  private fun resyncOwnerComponents(masterEntityId: EntityId) {
    world.modify(masterEntityId) { id -> OwnerComponentResync.resync(this, id) }
      ?: LOG.warn { "Cannot resync components, master entity $masterEntityId is not alive" }
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
      availableBestias = bestiaInfos,
      bestiaSlotCount = bestiaSlots.getTotalSlotCount(accountId)
    )
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
