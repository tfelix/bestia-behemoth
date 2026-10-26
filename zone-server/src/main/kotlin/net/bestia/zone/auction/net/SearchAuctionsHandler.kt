package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SearchAuctionsHandler(
  private val auctionService: AuctionService,
) : IoMessageHandler<SearchAuctionsCMSG> {

  override val wire = decoder(MessageCase.SEARCH_AUCTIONS) { accountId, envelope ->
    SearchAuctionsCMSG.fromBnet(accountId, envelope.searchAuctions)
  }

  override fun handle(msg: SearchAuctionsCMSG): Boolean {
    auctionService.search(msg.playerId, msg.query)

    return true
  }
}
