package net.bestia.zone.entity

import net.bestia.bnet.proto.ActiveEntitySmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class ActiveEntitySMSG(
  val entityId: EntityId
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val active = ActiveEntitySmsgProto.ActiveEntitySMSG.newBuilder()
      .setEntityId(entityId)

    return EnvelopeProto.Envelope.newBuilder()
      .setActiveEntity(active)
      .build()
  }
}
