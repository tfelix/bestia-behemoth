package net.bestia.zone.guild.net

import net.bestia.bnet.proto.LeaveGuildCmsgProto
import net.bestia.zone.message.CMSG

data class LeaveGuildCMSG(
  override val playerId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, @Suppress("UNUSED_PARAMETER") proto: LeaveGuildCmsgProto.LeaveGuildCMSG): LeaveGuildCMSG {
      return LeaveGuildCMSG(playerId = accountId)
    }
  }
}
