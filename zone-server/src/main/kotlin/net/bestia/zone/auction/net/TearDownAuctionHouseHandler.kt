package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PlayerHouseService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class TearDownAuctionHouseHandler(
  private val service: PlayerHouseService,
) : IoMessageHandler<TearDownAuctionHouseCMSG> {

  override val wire = decoder(MessageCase.TEAR_DOWN_AUCTION_HOUSE) { accountId, envelope ->
    TearDownAuctionHouseCMSG.fromBnet(accountId, envelope.tearDownAuctionHouse)
  }

  override fun handle(msg: TearDownAuctionHouseCMSG): Boolean {
    service.tearDown(msg.playerId, msg.houseEntityId)

    return true
  }
}
