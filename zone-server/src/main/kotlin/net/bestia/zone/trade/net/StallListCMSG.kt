package net.bestia.zone.trade.net

import net.bestia.bnet.proto.StallListCmsgProto
import net.bestia.zone.message.CMSG

data class StallListCMSG(
  override val playerId: Long,
  val stallEntityId: Long,
  val playerItemId: Long,
  val amount: Int,
  val price: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: StallListCmsgProto.StallListCMSG): StallListCMSG {
      return StallListCMSG(accountId, proto.stallEntityId, proto.playerItemId, proto.amount, proto.price)
    }
  }
}
