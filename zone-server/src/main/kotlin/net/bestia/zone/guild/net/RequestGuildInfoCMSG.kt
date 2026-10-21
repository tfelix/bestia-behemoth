package net.bestia.zone.guild.net

import net.bestia.bnet.proto.RequestGuildInfoCmsgProto
import net.bestia.zone.message.CMSG

data class RequestGuildInfoCMSG(
  override val playerId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, @Suppress("UNUSED_PARAMETER") proto: RequestGuildInfoCmsgProto.RequestGuildInfoCMSG): RequestGuildInfoCMSG {
      return RequestGuildInfoCMSG(playerId = accountId)
    }
  }
}
