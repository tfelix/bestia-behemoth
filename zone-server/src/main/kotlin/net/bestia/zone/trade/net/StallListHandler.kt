package net.bestia.zone.trade.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.stall.StreetStalls
import org.springframework.stereotype.Component

@Component
class StallListHandler(
  private val stalls: StreetStalls,
) : IoMessageHandler<StallListCMSG> {

  override val wire = decoder(MessageCase.STALL_LIST) { accountId, envelope ->
    StallListCMSG.fromBnet(accountId, envelope.stallList)
  }

  override fun handle(msg: StallListCMSG): Boolean {
    stalls.list(msg.playerId, msg.stallEntityId, msg.playerItemId, msg.amount, msg.price)

    return true
  }
}
