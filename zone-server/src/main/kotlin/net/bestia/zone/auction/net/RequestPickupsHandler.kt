package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PickupService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class RequestPickupsHandler(
  private val service: PickupService,
) : IoMessageHandler<RequestPickupsCMSG> {

  override val wire = decoder(MessageCase.REQUEST_PICKUPS) { accountId, envelope ->
    RequestPickupsCMSG.fromBnet(accountId, envelope.requestPickups)
  }

  override fun handle(msg: RequestPickupsCMSG): Boolean {
    service.show(msg.playerId)

    return true
  }
}
