package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class BuyAuctionListingHandler(
  private val service: AuctionService,
) : IoMessageHandler<BuyAuctionListingCMSG> {

  override val wire = decoder(MessageCase.BUY_AUCTION_LISTING) { accountId, envelope ->
    BuyAuctionListingCMSG.fromBnet(accountId, envelope.buyAuctionListing)
  }

  override fun handle(msg: BuyAuctionListingCMSG): Boolean {
    service.buy(msg.playerId, msg.houseEntityId, msg.listingId)

    return true
  }
}
