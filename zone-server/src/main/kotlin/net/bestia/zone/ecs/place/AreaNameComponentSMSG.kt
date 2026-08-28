package net.bestia.zone.ecs.place

import net.bestia.bnet.proto.AreaNameComponentSMSGProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.EntitySMSG

data class AreaNameComponentSMSG(
  override val entityId: Long,
  val name: String,
  val radius: Long
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val component = AreaNameComponentSMSGProto.AreaNameComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setName(name)
      .setRadius(radius)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompAreaName(component)
      .build()
  }
}
