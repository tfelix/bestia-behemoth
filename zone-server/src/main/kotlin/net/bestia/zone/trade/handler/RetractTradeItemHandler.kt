package net.bestia.zone.trade.handler

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.RetractTradeItemCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class RetractTradeItemHandler(
  private val tradeService: TradeService,
) : IoMessageHandler<RetractTradeItemCMSG> {

  override val wire = decoder(MessageCase.RETRACT_TRADE_ITEM) { accountId, envelope ->
    RetractTradeItemCMSG.fromBnet(accountId, envelope.retractTradeItem)
  }


  override fun handle(msg: RetractTradeItemCMSG): Boolean {
    tradeService.retractItem(msg.playerId, msg.tradeId, msg.offerSlotId)

    return true
  }
}
