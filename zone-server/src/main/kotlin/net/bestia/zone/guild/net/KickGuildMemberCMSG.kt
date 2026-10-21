package net.bestia.zone.guild.net

import net.bestia.bnet.proto.KickGuildMemberCmsgProto
import net.bestia.zone.message.CMSG

data class KickGuildMemberCMSG(
  override val playerId: Long,
  val masterId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: KickGuildMemberCmsgProto.KickGuildMemberCMSG): KickGuildMemberCMSG {
      return KickGuildMemberCMSG(
        playerId = accountId,
        masterId = proto.masterId,
      )
    }
  }
}
