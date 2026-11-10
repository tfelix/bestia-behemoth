package net.bestia.zone.auction.net

import net.bestia.bnet.proto.CutTradeLinkCmsgProto
import net.bestia.zone.message.CMSG

data class CutTradeLinkCMSG(
  override val playerId: Long,
  val houseEntityId: Long,
  val partnerMasterId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CutTradeLinkCmsgProto.CutTradeLinkCMSG): CutTradeLinkCMSG {
      return CutTradeLinkCMSG(
        playerId = accountId,
        houseEntityId = msg.houseEntityId,
        partnerMasterId = msg.partnerMasterId,
      )
    }
  }
}
