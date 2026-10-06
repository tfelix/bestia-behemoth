package net.bestia.zone.trade.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class AnswerTradeRequestHandler(
  private val tradeService: TradeService,
) : IoMessageHandler<AnswerTradeRequestCMSG> {

  override val wire = decoder(MessageCase.ANSWER_TRADE_REQUEST) { accountId, envelope ->
    AnswerTradeRequestCMSG.fromBnet(accountId, envelope.answerTradeRequest)
  }


  override fun handle(msg: AnswerTradeRequestCMSG): Boolean {
    tradeService.answerRequest(msg.playerId, msg.tradeId, msg.accept)

    return true
  }
}
