package net.bestia.zone.trade.handler

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.OfferTradeItemCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class OfferTradeItemHandler(
  private val tradeService: TradeService,
) : IoMessageHandler<OfferTradeItemCMSG> {

  override val wire = decoder(MessageCase.OFFER_TRADE_ITEM) { accountId, envelope ->
    OfferTradeItemCMSG.fromBnet(accountId, envelope.offerTradeItem)
  }


  override fun handle(msg: OfferTradeItemCMSG): Boolean {
    tradeService.offerItem(msg.playerId, msg.tradeId, msg.itemId, msg.uniqueId, msg.amount)

    return true
  }
}
