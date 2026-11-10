package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PickupService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class PostPickupHandler(
  private val service: PickupService,
) : IoMessageHandler<PostPickupCMSG> {

  override val wire = decoder(MessageCase.POST_PICKUP) { accountId, envelope ->
    PostPickupCMSG.fromBnet(accountId, envelope.postPickup)
  }

  override fun handle(msg: PostPickupCMSG): Boolean {
    service.post(msg.playerId, msg.pickupId)

    return true
  }
}
