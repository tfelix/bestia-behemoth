package net.bestia.zone.auction.net

import net.bestia.bnet.proto.TearDownAuctionHouseCmsgProto
import net.bestia.zone.message.CMSG

data class TearDownAuctionHouseCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: TearDownAuctionHouseCmsgProto.TearDownAuctionHouseCMSG): TearDownAuctionHouseCMSG {
      return TearDownAuctionHouseCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
      )
    }
  }
}
