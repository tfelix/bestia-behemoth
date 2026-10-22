package net.bestia.zone.guild

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.GuildSkillCooldownSmsgProto
import net.bestia.zone.message.SMSG

data class GuildSkillCooldownSMSG(
  val skillId: Long,
  val remainingMillis: Long,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = GuildSkillCooldownSmsgProto.GuildSkillCooldownSMSG.newBuilder()
      .setSkillId(skillId)
      .setRemainingMs(remainingMillis)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setGuildSkillCooldown(proto)
      .build()
  }
}
