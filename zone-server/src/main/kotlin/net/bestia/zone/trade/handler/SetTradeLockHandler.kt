package net.bestia.zone.trade.handler

import net.bestia.zone.message.IoMessageHandler
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
) : IoMessageHandler<SetTradeLockCMSG> {

  override val handles = SetTradeLockCMSG::class


  override fun handle(msg: SetTradeLockCMSG): Boolean {
    tradeService.setLock(msg.playerId, msg.tradeId, msg.locked)

    return true
  }
}
