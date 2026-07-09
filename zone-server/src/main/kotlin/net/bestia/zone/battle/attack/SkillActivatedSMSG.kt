package net.bestia.zone.battle.attack

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SkillActivatedSMSGProto
import net.bestia.zone.message.SMSG

data class SkillActivatedSMSG(
  val entityId: Long,
  val attackId: Long,
  val skillLevel: Int
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val skillActivatedSMSG = SkillActivatedSMSGProto.SkillActivatedSMSG.newBuilder()
      .setEntityId(entityId)
      .setAttackId(attackId)
      .setSkillLevel(skillLevel)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setSkillActivated(skillActivatedSMSG)
      .build()
  }
}
