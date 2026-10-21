package net.bestia.zone.guild.net

import net.bestia.bnet.proto.CreateGuildCmsgProto
import net.bestia.zone.message.CMSG

data class CreateGuildCMSG(
  override val playerId: Long,
  val name: String,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: CreateGuildCmsgProto.CreateGuildCMSG): CreateGuildCMSG {
      return CreateGuildCMSG(
        playerId = accountId,
        name = proto.name,
      )
    }
  }
}
