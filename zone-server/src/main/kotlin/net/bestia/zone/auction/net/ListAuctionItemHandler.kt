package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.AuctionService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class ListAuctionItemHandler(
  private val auctionService: AuctionService,
) : IoMessageHandler<ListAuctionItemCMSG> {

  override val wire = decoder(MessageCase.LIST_AUCTION_ITEM) { accountId, envelope ->
    ListAuctionItemCMSG.fromBnet(accountId, envelope.listAuctionItem)
  }

  override fun handle(msg: ListAuctionItemCMSG): Boolean {
    auctionService.list(msg.playerId, msg.houseEntityId, msg.playerItemId, msg.amount, msg.price)

    return true
  }
}
