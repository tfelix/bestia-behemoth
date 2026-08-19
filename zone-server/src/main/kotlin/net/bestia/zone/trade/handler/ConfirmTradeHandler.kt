package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.trade.ConfirmTradeCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class ConfirmTradeHandler(
  private val tradeService: TradeService,
) : InMessageProcessor.IncomingMessageHandler<ConfirmTradeCMSG> {

  override val handles = ConfirmTradeCMSG::class

  override fun handle(msg: ConfirmTradeCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.confirm(msg.playerId, msg.tradeId)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
