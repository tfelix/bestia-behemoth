package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PickupService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class CollectPickupHandler(
  private val service: PickupService,
) : IoMessageHandler<CollectPickupCMSG> {

  override val wire = decoder(MessageCase.COLLECT_PICKUP) { accountId, envelope ->
    CollectPickupCMSG.fromBnet(accountId, envelope.collectPickup)
  }

  override fun handle(msg: CollectPickupCMSG): Boolean {
    service.collect(msg.playerId, msg.pickupId)

    return true
  }
}
