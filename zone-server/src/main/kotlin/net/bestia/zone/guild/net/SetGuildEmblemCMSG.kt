package net.bestia.zone.guild.net

import net.bestia.bnet.proto.SetGuildEmblemCmsgProto
import net.bestia.zone.message.CMSG

class SetGuildEmblemCMSG(
  override val playerId: Long,
  val png: ByteArray,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: SetGuildEmblemCmsgProto.SetGuildEmblemCMSG): SetGuildEmblemCMSG {
      return SetGuildEmblemCMSG(
        playerId = accountId,
        png = proto.png.toByteArray(),
      )
    }
  }
}
