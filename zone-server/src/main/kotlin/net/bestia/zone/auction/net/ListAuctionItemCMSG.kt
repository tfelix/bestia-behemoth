package net.bestia.zone.auction.net

import net.bestia.bnet.proto.ListAuctionItemCmsgProto
import net.bestia.zone.message.CMSG

data class ListAuctionItemCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val playerItemId: Long,
  val amount: Int,
  val price: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: ListAuctionItemCmsgProto.ListAuctionItemCMSG): ListAuctionItemCMSG {
      return ListAuctionItemCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        playerItemId = msg.playerItemId,
        amount = msg.amount,
        price = msg.price,
      )
    }
  }
}
