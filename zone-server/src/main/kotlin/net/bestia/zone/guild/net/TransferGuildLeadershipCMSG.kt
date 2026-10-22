package net.bestia.zone.guild.net

import net.bestia.bnet.proto.TransferGuildLeadershipCmsgProto
import net.bestia.zone.message.CMSG

data class TransferGuildLeadershipCMSG(
  override val playerId: Long,
  val masterId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: TransferGuildLeadershipCmsgProto.TransferGuildLeadershipCMSG): TransferGuildLeadershipCMSG {
      return TransferGuildLeadershipCMSG(
        playerId = accountId,
        masterId = proto.masterId,
      )
    }
  }
}
