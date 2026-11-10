package net.bestia.zone.auction.net

import net.bestia.bnet.proto.BidAuctionCmsgProto
import net.bestia.zone.message.CMSG

data class BidAuctionCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val listingId: Long,
  val amount: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: BidAuctionCmsgProto.BidAuctionCMSG): BidAuctionCMSG {
      return BidAuctionCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        listingId = msg.listingId,
        amount = msg.amount,
      )
    }
  }
}
