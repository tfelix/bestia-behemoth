package net.bestia.zone.auction.net

import net.bestia.bnet.proto.CancelAuctionListingCmsgProto
import net.bestia.zone.message.CMSG

data class CancelAuctionListingCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val listingId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CancelAuctionListingCmsgProto.CancelAuctionListingCMSG): CancelAuctionListingCMSG {
      return CancelAuctionListingCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        listingId = msg.listingId,
      )
    }
  }
}
