package net.bestia.zone.guild.net

import net.bestia.bnet.proto.SetGuildMemberRankCmsgProto
import net.bestia.zone.message.CMSG

data class SetGuildMemberRankCMSG(
  override val playerId: Long,
  val masterId: Long,
  val rankId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SetGuildMemberRankCmsgProto.SetGuildMemberRankCMSG): SetGuildMemberRankCMSG {
      return SetGuildMemberRankCMSG(
        playerId = accountId,
        masterId = proto.masterId,
        rankId = proto.rankId,
      )
    }
  }
}
