package net.bestia.zone.ecs.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LevelComponentSMSGProto
import net.bestia.bnet.proto.SpeedComponentSMSGProto
import net.bestia.zone.message.entity.EntitySMSG

data class LevelSMSG(
  override val entityId: Long,
  val level: Int,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val levelComponent = LevelComponentSMSGProto.LevelComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setLevel(level)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompLevel(levelComponent)
      .build()
  }
}
