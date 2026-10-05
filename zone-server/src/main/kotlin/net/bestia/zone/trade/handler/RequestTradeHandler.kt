package net.bestia.zone.trade.handler

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
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
) : IoMessageHandler<RequestTradeCMSG> {

  override val wire = decoder(MessageCase.REQUEST_TRADE) { accountId, envelope ->
    RequestTradeCMSG.fromBnet(accountId, envelope.requestTrade)
  }


  override fun handle(msg: RequestTradeCMSG): Boolean {
    tradeService.requestTrade(msg.playerId, msg.targetEntityId)

    return true
  }
}
