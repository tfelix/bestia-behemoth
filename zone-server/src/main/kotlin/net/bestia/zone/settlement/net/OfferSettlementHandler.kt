package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto.OpError
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component

@Component
class OfferSettlementHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val towns: TownService,
  private val outMessageProcessor: OutMessageProcessor,
) : TickMessageHandler<OfferSettlementCMSG> {

  override val wire = decoder(MessageCase.OFFER_SETTLEMENT) { accountId, envelope ->
    OfferSettlementCMSG.fromBnet(accountId, envelope.offerSettlement)
  }

  override fun handle(world: World, msg: OfferSettlementCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)

    towns.offer(world, masterEntityId, msg.signEntityId, msg.masterName)?.let { refusal ->
      val args = if (refusal == OpError.SETTLEMENT_CANNOT_RECEIVE) listOf(msg.masterName) else emptyList()
      outMessageProcessor.sendToPlayer(msg.playerId, OperationErrorSMSG(refusal, args))
    }

    return true
  }
}
