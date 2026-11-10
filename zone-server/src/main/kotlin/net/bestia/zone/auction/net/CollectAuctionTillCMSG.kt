package net.bestia.zone.auction.net

import net.bestia.bnet.proto.CollectAuctionTillCmsgProto
import net.bestia.zone.message.CMSG

data class CollectAuctionTillCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CollectAuctionTillCmsgProto.CollectAuctionTillCMSG): CollectAuctionTillCMSG {
      return CollectAuctionTillCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
      )
    }
  }
}
