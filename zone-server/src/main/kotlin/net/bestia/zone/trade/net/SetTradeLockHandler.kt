package net.bestia.zone.trade.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
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

  override val wire = decoder(MessageCase.SET_TRADE_LOCK) { accountId, envelope ->
    SetTradeLockCMSG.fromBnet(accountId, envelope.setTradeLock)
  }


  override fun handle(msg: SetTradeLockCMSG): Boolean {
    tradeService.setLock(msg.playerId, msg.tradeId, msg.locked)

    return true
  }
}
