package net.bestia.zone.ecs.place

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PlaceComponentSMSGProto
import net.bestia.zone.message.EntitySMSG

data class PlaceComponentSMSG(
  override val entityId: Long,
  val name: String
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val component = PlaceComponentSMSGProto.PlaceComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .setName(name)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompPlace(component)
      .build()
  }
}
