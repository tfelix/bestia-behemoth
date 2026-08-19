package net.bestia.zone.trade.handler

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.trade.SetTradeLockCMSG
import net.bestia.zone.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Thin by design: every refusal a trade can answer with is a state [TradeService] owns, so it also owns
 * telling the client - there is nothing left here to map.
 */
@Component
class SetTradeLockHandler(
  private val tradeService: TradeService,
) : InMessageProcessor.IncomingMessageHandler<SetTradeLockCMSG> {

  override val handles = SetTradeLockCMSG::class

  override fun handle(msg: SetTradeLockCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    tradeService.setLock(msg.playerId, msg.tradeId, msg.locked)

    return true
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
