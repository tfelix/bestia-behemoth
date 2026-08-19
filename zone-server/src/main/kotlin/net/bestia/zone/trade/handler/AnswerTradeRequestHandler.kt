package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.trade.AnswerTradeRequestCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class AnswerTradeRequestHandler(
  private val tradeService: TradeService,
) : InMessageProcessor.IncomingMessageHandler<AnswerTradeRequestCMSG> {

  override val handles = AnswerTradeRequestCMSG::class

  override fun handle(msg: AnswerTradeRequestCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.answerRequest(msg.playerId, msg.tradeId, msg.accept)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
