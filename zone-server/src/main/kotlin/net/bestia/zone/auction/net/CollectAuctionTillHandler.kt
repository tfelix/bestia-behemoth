package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PlayerHouseService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class CollectAuctionTillHandler(
  private val service: PlayerHouseService,
) : IoMessageHandler<CollectAuctionTillCMSG> {

  override val wire = decoder(MessageCase.COLLECT_AUCTION_TILL) { accountId, envelope ->
    CollectAuctionTillCMSG.fromBnet(accountId, envelope.collectAuctionTill)
  }

  override fun handle(msg: CollectAuctionTillCMSG): Boolean {
    service.collectTill(msg.playerId, msg.houseEntityId)

    return true
  }
}
