package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class CancelAuctionListingHandler(
  private val service: AuctionService,
) : IoMessageHandler<CancelAuctionListingCMSG> {

  override val wire = decoder(MessageCase.CANCEL_AUCTION_LISTING) { accountId, envelope ->
    CancelAuctionListingCMSG.fromBnet(accountId, envelope.cancelAuctionListing)
  }

  override fun handle(msg: CancelAuctionListingCMSG): Boolean {
    service.takeDown(msg.playerId, msg.houseEntityId, msg.listingId)

    return true
  }
}
