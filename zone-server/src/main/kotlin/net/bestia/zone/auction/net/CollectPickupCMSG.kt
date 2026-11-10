package net.bestia.zone.auction.net

import net.bestia.bnet.proto.CollectPickupCmsgProto
import net.bestia.zone.message.CMSG

data class CollectPickupCMSG(
  override val playerId: Long,
  val pickupId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: CollectPickupCmsgProto.CollectPickupCMSG): CollectPickupCMSG {
      return CollectPickupCMSG(
        playerId = accountId,
        pickupId = msg.pickupId,
      )
    }
  }
}
