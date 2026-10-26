package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionClaimService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class RequestAuctionClaimsHandler(
  private val service: AuctionClaimService,
) : IoMessageHandler<RequestAuctionClaimsCMSG> {

  override val wire = decoder(MessageCase.REQUEST_AUCTION_CLAIMS) { accountId, envelope ->
    RequestAuctionClaimsCMSG.fromBnet(accountId, envelope.requestAuctionClaims)
  }

  override fun handle(msg: RequestAuctionClaimsCMSG): Boolean {
    service.show(msg.playerId, msg.houseEntityId)

    return true
  }
}
