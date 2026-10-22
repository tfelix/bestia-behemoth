package net.bestia.zone.guild.net

import net.bestia.bnet.proto.LiftGuildExpulsionCmsgProto
import net.bestia.zone.message.CMSG

data class LiftGuildExpulsionCMSG(
  override val playerId: Long,
  val masterId: Long,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: LiftGuildExpulsionCmsgProto.LiftGuildExpulsionCMSG): LiftGuildExpulsionCMSG {
      return LiftGuildExpulsionCMSG(
        playerId = accountId,
        masterId = proto.masterId,
      )
    }
  }
}
