package net.bestia.zone.trade.net

import net.bestia.bnet.proto.StallUnlistCmsgProto
import net.bestia.zone.message.CMSG

data class StallUnlistCMSG(
  override val playerId: Long,
  val stallEntityId: Long,
  val listingId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: StallUnlistCmsgProto.StallUnlistCMSG): StallUnlistCMSG {
      return StallUnlistCMSG(accountId, proto.stallEntityId, proto.listingId)
    }
  }
}
