package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SkillPointsSMSGProto
import net.bestia.zone.message.EntitySMSG

data class SkillPointsComponentSMSG(
  override val entityId: Long,
  val points: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val skillPoints = SkillPointsSMSGProto.SkillPointsSMSG.newBuilder()
      .setEntityId(entityId)
      .setPoints(points)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompSkillPoints(skillPoints)
      .build()
  }
}
