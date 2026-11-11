package net.bestia.zone.trade.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.stall.StreetStalls
import org.springframework.stereotype.Component

@Component
class StallBuyHandler(
  private val stalls: StreetStalls,
) : IoMessageHandler<StallBuyCMSG> {

  override val wire = decoder(MessageCase.STALL_BUY) { accountId, envelope ->
    StallBuyCMSG.fromBnet(accountId, envelope.stallBuy)
  }

  override fun handle(msg: StallBuyCMSG): Boolean {
    stalls.buy(msg.playerId, msg.stallEntityId, msg.listingId)

    return true
  }
}
