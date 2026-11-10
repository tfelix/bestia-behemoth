package net.bestia.zone.auction.net

import net.bestia.bnet.proto.SetAuctionFeeCmsgProto
import net.bestia.zone.message.CMSG

data class SetAuctionFeeCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val percent: Int,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: SetAuctionFeeCmsgProto.SetAuctionFeeCMSG): SetAuctionFeeCMSG {
      return SetAuctionFeeCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        percent = msg.percent,
      )
    }
  }
}
