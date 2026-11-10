package net.bestia.zone.casting

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SkillCooldownSmsgProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class SkillCooldownSMSG(
  val entityId: EntityId,
  val skillId: Long,
  val remainingMillis: Long,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val proto = SkillCooldownSmsgProto.SkillCooldownSMSG.newBuilder()
      .setEntityId(entityId)
      .setSkillId(skillId)
      .setRemainingMs(remainingMillis)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSkillCooldown(proto)
      .build()
  }
}
