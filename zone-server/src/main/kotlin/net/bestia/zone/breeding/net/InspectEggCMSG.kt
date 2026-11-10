package net.bestia.zone.breeding.net

import net.bestia.bnet.proto.InspectEggCmsgProto
import net.bestia.zone.message.CMSG

/** The master asks what an egg in their bag holds. */
data class InspectEggCMSG(
  override val playerId: Long,
  val uniqueId: Long,
) : CMSG {

  companion object {
    fun fromBnet(accountId: Long, proto: InspectEggCmsgProto.InspectEggCMSG): InspectEggCMSG {
      return InspectEggCMSG(playerId = accountId, uniqueId = proto.uniqueId)
    }
  }
}
