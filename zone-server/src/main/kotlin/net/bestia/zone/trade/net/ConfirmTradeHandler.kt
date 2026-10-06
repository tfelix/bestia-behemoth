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
class ConfirmTradeHandler(
  private val tradeService: TradeService,
) : IoMessageHandler<ConfirmTradeCMSG> {

  override val wire = decoder(MessageCase.CONFIRM_TRADE) { accountId, envelope ->
    ConfirmTradeCMSG.fromBnet(accountId, envelope.confirmTrade)
  }


  override fun handle(msg: ConfirmTradeCMSG): Boolean {
    tradeService.confirm(msg.playerId, msg.tradeId)

    return true
  }
}
