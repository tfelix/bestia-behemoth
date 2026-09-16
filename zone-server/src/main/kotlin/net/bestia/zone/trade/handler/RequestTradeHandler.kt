package net.bestia.zone.trade.handler

import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.trade.RequestTradeCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class RequestTradeHandler(
  private val tradeService: TradeService,
) : InMessageProcessor.IncomingMessageHandler<RequestTradeCMSG> {

  override val handles = RequestTradeCMSG::class

  override fun handle(msg: RequestTradeCMSG): Boolean {
    tradeService.requestTrade(msg.playerId, msg.targetEntityId)

    return true
  }
}
