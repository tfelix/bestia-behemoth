package net.bestia.zone.component

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.HealthComponentSMSGProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.entity.EntitySMSG

data class HealthComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val healthComponent = HealthComponentSMSGProto.HealthComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setCurrent(current)
      .setMax(max)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompHealth(healthComponent)
      .build()
  }
}
