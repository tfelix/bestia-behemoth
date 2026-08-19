package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
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
) : InMessageProcessor.IncomingMessageHandler<CancelTradeCMSG> {

  override val handles = CancelTradeCMSG::class

  override fun handle(msg: CancelTradeCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.cancel(msg.playerId, msg.tradeId)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
