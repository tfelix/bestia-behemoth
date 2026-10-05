package net.bestia.zone.trade.handler

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.CancelTradeCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class CancelTradeHandler(
  private val tradeService: TradeService,
) : IoMessageHandler<CancelTradeCMSG> {

  override val wire = decoder(MessageCase.CANCEL_TRADE) { accountId, envelope ->
    CancelTradeCMSG.fromBnet(accountId, envelope.cancelTrade)
  }


  override fun handle(msg: CancelTradeCMSG): Boolean {
    tradeService.cancel(msg.playerId, msg.tradeId)

    return true
  }
}
