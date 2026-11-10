package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class BidAuctionHandler(
  private val service: AuctionService,
) : IoMessageHandler<BidAuctionCMSG> {

  override val wire = decoder(MessageCase.BID_AUCTION) { accountId, envelope ->
    BidAuctionCMSG.fromBnet(accountId, envelope.bidAuction)
  }

  override fun handle(msg: BidAuctionCMSG): Boolean {
    service.bid(msg.playerId, msg.houseEntityId, msg.listingId, msg.amount)

    return true
  }
}
