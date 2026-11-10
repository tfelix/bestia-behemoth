package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.TradeAgreementService
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.TickMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

@Component
class AnswerTradeAgreementHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val agreements: TradeAgreementService,
) : TickMessageHandler<AnswerTradeAgreementCMSG> {

  override val wire = decoder(MessageCase.ANSWER_TRADE_AGREEMENT) { accountId, envelope ->
    AnswerTradeAgreementCMSG.fromBnet(accountId, envelope.answerTradeAgreement)
  }

  override fun handle(world: World, msg: AnswerTradeAgreementCMSG): Boolean {
    agreements.answer(world, connectionInfoService.getSelectedMasterEntityId(msg.playerId), msg.offerId, msg.accept)

    return true
  }
}
