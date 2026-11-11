package net.bestia.zone.trade.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.trade.stall.StreetStalls
import org.springframework.stereotype.Component

@Component
class StallUnlistHandler(
  private val stalls: StreetStalls,
) : IoMessageHandler<StallUnlistCMSG> {

  override val wire = decoder(MessageCase.STALL_UNLIST) { accountId, envelope ->
    StallUnlistCMSG.fromBnet(accountId, envelope.stallUnlist)
  }

  override fun handle(msg: StallUnlistCMSG): Boolean {
    stalls.unlist(msg.playerId, msg.stallEntityId, msg.listingId)

    return true
  }
}
