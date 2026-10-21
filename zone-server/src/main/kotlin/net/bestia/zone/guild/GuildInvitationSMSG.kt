package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildInvitationSmsgProto
import net.bestia.zone.message.SMSG

data class GuildInvitationSMSG(
  val invitationId: Long,
  val guildName: String,
  val invitedByMaster: String,
  val expiresInSeconds: Int,
) : SMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildInvitationSmsgProto.GuildInvitationSMSG.newBuilder()
      .setInvitationId(invitationId)
      .setGuildName(guildName)
      .setInvitedByMaster(invitedByMaster)
      .setExpiresInSeconds(expiresInSeconds)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildInvitation(proto)
      .build()
  }
}
