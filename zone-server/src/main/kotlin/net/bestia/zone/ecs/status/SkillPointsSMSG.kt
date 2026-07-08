package net.bestia.zone.ecs.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SkillPointsSMSGProto
import net.bestia.zone.message.entity.EntitySMSG

data class SkillPointsSMSG(
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
