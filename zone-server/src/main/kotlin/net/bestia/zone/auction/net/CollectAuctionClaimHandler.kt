package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionClaimService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class CollectAuctionClaimHandler(
  private val service: AuctionClaimService,
) : IoMessageHandler<CollectAuctionClaimCMSG> {

  override val wire = decoder(MessageCase.COLLECT_AUCTION_CLAIM) { accountId, envelope ->
    CollectAuctionClaimCMSG.fromBnet(accountId, envelope.collectAuctionClaim)
  }

  override fun handle(msg: CollectAuctionClaimCMSG): Boolean {
    service.collect(msg.playerId, msg.houseEntityId, msg.claimId)

    return true
  }
}
