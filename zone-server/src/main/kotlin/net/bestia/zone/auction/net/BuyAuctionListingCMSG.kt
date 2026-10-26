package net.bestia.zone.auction.net

import net.bestia.bnet.proto.BuyAuctionListingCmsgProto
import net.bestia.zone.message.CMSG

data class BuyAuctionListingCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val listingId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: BuyAuctionListingCmsgProto.BuyAuctionListingCMSG): BuyAuctionListingCMSG {
      return BuyAuctionListingCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        listingId = msg.listingId,
      )
    }
  }
}
