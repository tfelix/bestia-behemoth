package net.bestia.zone.guild.net

import net.bestia.bnet.proto.DisbandGuildCmsgProto
import net.bestia.zone.message.CMSG

data class DisbandGuildCMSG(
  override val playerId: Long,
  val confirmName: String,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: DisbandGuildCmsgProto.DisbandGuildCMSG): DisbandGuildCMSG {
      return DisbandGuildCMSG(
        playerId = accountId,
        confirmName = proto.confirmName,
      )
    }
  }
}
