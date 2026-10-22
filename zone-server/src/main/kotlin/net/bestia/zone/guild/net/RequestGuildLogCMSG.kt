package net.bestia.zone.guild.net

import net.bestia.bnet.proto.RequestGuildLogCmsgProto
import net.bestia.zone.message.CMSG

data class RequestGuildLogCMSG(
  override val playerId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, @Suppress("UNUSED_PARAMETER") proto: RequestGuildLogCmsgProto.RequestGuildLogCMSG): RequestGuildLogCMSG {
      return RequestGuildLogCMSG(playerId = accountId)
    }
  }
}
