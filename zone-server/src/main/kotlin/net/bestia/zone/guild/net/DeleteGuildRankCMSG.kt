package net.bestia.zone.guild.net

import net.bestia.bnet.proto.DeleteGuildRankCmsgProto
import net.bestia.zone.message.CMSG

data class DeleteGuildRankCMSG(
  override val playerId: Long,
  val rankId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: DeleteGuildRankCmsgProto.DeleteGuildRankCMSG): DeleteGuildRankCMSG {
      return DeleteGuildRankCMSG(
        playerId = accountId,
        rankId = proto.rankId,
      )
    }
  }
}
