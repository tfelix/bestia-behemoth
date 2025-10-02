package net.bestia.zone.ecs.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.ExpComponentSMSGProto
import net.bestia.zone.message.entity.EntitySMSG

data class ExpSMSG(
  override val entityId: Long,
  val exp: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val expComponent = ExpComponentSMSGProto.ExpComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setExp(exp)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompExp(expComponent)
      .build()
  }
}