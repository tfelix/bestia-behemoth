package net.bestia.zone.guild.net

import net.bestia.bnet.proto.SetGuildMotdCmsgProto
import net.bestia.zone.message.CMSG

data class SetGuildMotdCMSG(
  override val playerId: Long,
  val title: String,
  val body: String,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SetGuildMotdCmsgProto.SetGuildMotdCMSG): SetGuildMotdCMSG {
      return SetGuildMotdCMSG(
        playerId = accountId,
        title = proto.title,
        body = proto.body,
      )
    }
  }
}
