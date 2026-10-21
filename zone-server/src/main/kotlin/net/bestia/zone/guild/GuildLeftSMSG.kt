package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildLeftSmsgProto
import net.bestia.zone.message.SMSG

data class GuildLeftSMSG(
  val guildId: Long,
  val reason: Reason,
) : SMSG {

  enum class Reason { LEFT, KICKED, DISBANDED }

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildLeftSmsgProto.GuildLeftSMSG.newBuilder()
      .setGuildId(guildId)
      .setReason(
        when (reason) {
          Reason.LEFT -> GuildLeftSmsgProto.GuildLeftReason.GUILD_LEFT_REASON_LEFT
          Reason.KICKED -> GuildLeftSmsgProto.GuildLeftReason.GUILD_LEFT_REASON_KICKED
          Reason.DISBANDED -> GuildLeftSmsgProto.GuildLeftReason.GUILD_LEFT_REASON_DISBANDED
        }
      )
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildLeft(proto)
      .build()
  }
}
