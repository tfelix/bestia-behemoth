package net.bestia.zone.auction.net

import net.bestia.bnet.proto.RequestPickupsCmsgProto
import net.bestia.zone.message.CMSG

data class RequestPickupsCMSG(
  override val playerId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: RequestPickupsCmsgProto.RequestPickupsCMSG): RequestPickupsCMSG {
      return RequestPickupsCMSG(
        playerId = accountId,
      )
    }
  }
}
