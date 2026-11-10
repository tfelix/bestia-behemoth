package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.TakeEggCmsgProto
import net.bestia.zone.message.CMSG

/** The master takes the egg out of a farm or an incubator. */
data class TakeEggCMSG(
  override val playerId: Long,
  val structureId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: TakeEggCmsgProto.TakeEggCMSG): TakeEggCMSG {
      return TakeEggCMSG(playerId = accountId, structureId = proto.structureId)
    }
  }
}
