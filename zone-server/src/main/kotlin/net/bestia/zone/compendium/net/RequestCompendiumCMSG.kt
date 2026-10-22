package net.bestia.zone.compendium.net

import net.bestia.bnet.proto.RequestCompendiumCmsgProto
import net.bestia.zone.message.CMSG

data class RequestCompendiumCMSG(
  override val playerId: Long,
) : CMSG {
  companion object {
    fun fromBnet(
      accountId: Long,
      @Suppress("UNUSED_PARAMETER") proto: RequestCompendiumCmsgProto.RequestCompendiumCMSG
    ): RequestCompendiumCMSG {
      return RequestCompendiumCMSG(playerId = accountId)
    }
  }
}
