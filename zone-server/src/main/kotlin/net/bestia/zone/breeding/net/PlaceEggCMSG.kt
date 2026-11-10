package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.PlaceEggCmsgProto
import net.bestia.zone.message.CMSG

/** The master puts an egg from their bag into their Incubator. */
data class PlaceEggCMSG(
  override val playerId: Long,
  val structureId: Long,
  val uniqueId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: PlaceEggCmsgProto.PlaceEggCMSG): PlaceEggCMSG {
      return PlaceEggCMSG(playerId = accountId, structureId = proto.structureId, uniqueId = proto.uniqueId)
    }
  }
}
