package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
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
) : InMessageProcessor.IncomingMessageHandler<RetractTradeItemCMSG> {

  override val handles = RetractTradeItemCMSG::class

  override fun handle(msg: RetractTradeItemCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.retractItem(msg.playerId, msg.tradeId, msg.offerSlotId)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
