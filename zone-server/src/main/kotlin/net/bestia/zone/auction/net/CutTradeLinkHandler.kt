package net.bestia.zone.auction.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.auction.PlayerHouseService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class CutTradeLinkHandler(
  private val service: PlayerHouseService,
) : IoMessageHandler<CutTradeLinkCMSG> {

  override val wire = decoder(MessageCase.CUT_TRADE_LINK) { accountId, envelope ->
    CutTradeLinkCMSG.fromBnet(accountId, envelope.cutTradeLink)
  }

  override fun handle(msg: CutTradeLinkCMSG): Boolean {
    service.cutLink(msg.playerId, msg.houseEntityId, msg.partnerMasterId)

    return true
  }
}
