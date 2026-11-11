package net.bestia.zone.trade.net

import net.bestia.bnet.proto.StallBuyCmsgProto
import net.bestia.zone.message.CMSG

data class StallBuyCMSG(
  override val playerId: Long,
  val stallEntityId: Long,
  val listingId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: StallBuyCmsgProto.StallBuyCMSG): StallBuyCMSG {
      return StallBuyCMSG(accountId, proto.stallEntityId, proto.listingId)
    }
  }
}
