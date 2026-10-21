package net.bestia.zone.guild.net

import net.bestia.bnet.proto.InviteToGuildCmsgProto
import net.bestia.zone.message.CMSG

data class InviteToGuildCMSG(
  override val playerId: Long,
  val masterName: String,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: InviteToGuildCmsgProto.InviteToGuildCMSG): InviteToGuildCMSG {
      return InviteToGuildCMSG(
        playerId = accountId,
        masterName = proto.masterName,
      )
    }
  }
}
