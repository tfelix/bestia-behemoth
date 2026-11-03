package net.bestia.zone.item.net

import net.bestia.bnet.proto.SetQuiverCMSGProto
import net.bestia.zone.message.CMSG

data class SetQuiverCMSG(
  override val playerId: Long,
  val itemIds: List<Long>
) : CMSG {

  companion object {
    fun fromBnet(playerId: Long, bnet: SetQuiverCMSGProto.SetQuiverCMSG): SetQuiverCMSG {
      return SetQuiverCMSG(playerId, bnet.itemIdsList.map { it.toLong() })
    }
  }
}
