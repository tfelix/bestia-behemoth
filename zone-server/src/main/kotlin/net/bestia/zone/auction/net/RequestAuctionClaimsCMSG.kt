package net.bestia.zone.auction.net

import net.bestia.bnet.proto.RequestAuctionClaimsCmsgProto
import net.bestia.zone.message.CMSG

data class RequestAuctionClaimsCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RequestAuctionClaimsCmsgProto.RequestAuctionClaimsCMSG): RequestAuctionClaimsCMSG {
      return RequestAuctionClaimsCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
      )
    }
  }
}
