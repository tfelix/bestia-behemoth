package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PlayerHouseService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class SetAuctionFeeHandler(
  private val service: PlayerHouseService,
) : IoMessageHandler<SetAuctionFeeCMSG> {

  override val wire = decoder(MessageCase.SET_AUCTION_FEE) { accountId, envelope ->
    SetAuctionFeeCMSG.fromBnet(accountId, envelope.setAuctionFee)
  }

  override fun handle(msg: SetAuctionFeeCMSG): Boolean {
    service.setFee(msg.playerId, msg.houseEntityId, msg.percent)

    return true
  }
}
