package net.bestia.zone.ecs.movement

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SpeedComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class SpeedSMSG(
  override val entityId: Long,
  val speed: Float,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val speedComp = SpeedComponentSMSGProto.SpeedComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setSpeed(speed)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompSpeed(speedComp)
      .build()
  }
}