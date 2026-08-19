package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
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
) : InMessageProcessor.IncomingMessageHandler<OfferTradeItemCMSG> {

  override val handles = OfferTradeItemCMSG::class

  override fun handle(msg: OfferTradeItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.offerItem(msg.playerId, msg.tradeId, msg.itemId, msg.uniqueId, msg.amount)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
