package net.bestia.zone.auction.net

import net.bestia.bnet.proto.CollectAuctionClaimCmsgProto
import net.bestia.zone.message.CMSG

data class CollectAuctionClaimCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val claimId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CollectAuctionClaimCmsgProto.CollectAuctionClaimCMSG): CollectAuctionClaimCMSG {
      return CollectAuctionClaimCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        claimId = msg.claimId,
      )
    }
  }
}
