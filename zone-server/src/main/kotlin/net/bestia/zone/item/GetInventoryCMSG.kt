package net.bestia.zone.item

import net.bestia.bnet.proto.GetInventoryCmsgProto
import net.bestia.zone.message.CMSG

data class GetInventoryCMSG(
  override val playerId: Long,
  val entityId: Long
) : CMSG {
  companion object {
    fun fromBnet(playerId: Long, bnetMsg: GetInventoryCmsgProto.GetInventoryCMSG): GetInventoryCMSG {
      return GetInventoryCMSG(
        playerId = playerId,
        entityId = bnetMsg.entityId.toLong()
      )
    }
  }
}
