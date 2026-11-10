package net.bestia.zone.auction.net

import net.bestia.bnet.proto.PostPickupCmsgProto
import net.bestia.zone.message.CMSG

data class PostPickupCMSG(
  override val playerId: Long,
  val pickupId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, msg: PostPickupCmsgProto.PostPickupCMSG): PostPickupCMSG {
      return PostPickupCMSG(
        playerId = accountId,
        pickupId = msg.pickupId,
      )
    }
  }
}
