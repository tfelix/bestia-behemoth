package net.bestia.zone.settlement.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.settlement.TownService
import org.springframework.stereotype.Component

@Component
class AnswerSettlementOfferHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val towns: TownService,
) : TickMessageHandler<AnswerSettlementOfferCMSG> {

  override val wire = decoder(MessageCase.ANSWER_SETTLEMENT_OFFER) { accountId, envelope ->
    AnswerSettlementOfferCMSG.fromBnet(accountId, envelope.answerSettlementOffer)
  }

  override fun handle(world: World, msg: AnswerSettlementOfferCMSG): Boolean {
    val masterEntityId = connectionInfoService.getSelectedMasterEntityId(msg.playerId)
    towns.answer(world, masterEntityId, msg.townId, msg.accept)

    return true
  }
}
