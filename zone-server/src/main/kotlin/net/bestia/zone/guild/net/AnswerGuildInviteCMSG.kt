package net.bestia.zone.guild.net

import net.bestia.bnet.proto.AnswerGuildInviteCmsgProto
import net.bestia.zone.message.CMSG

data class AnswerGuildInviteCMSG(
  override val playerId: Long,
  val invitationId: Long,
  val accept: Boolean,
) : CMSG {
  companion object {
    fun fromBnet(accountId: Long, proto: AnswerGuildInviteCmsgProto.AnswerGuildInviteCMSG): AnswerGuildInviteCMSG {
      return AnswerGuildInviteCMSG(
        playerId = accountId,
        invitationId = proto.invitationId,
        accept = proto.accept,
      )
    }
  }
}
