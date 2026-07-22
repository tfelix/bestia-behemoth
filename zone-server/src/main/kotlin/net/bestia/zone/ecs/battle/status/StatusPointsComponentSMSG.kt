package net.bestia.zone.ecs.battle.status

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.StatusPointsSMSGProto
import net.bestia.zone.message.EntitySMSG

data class StatusPointsComponentSMSG(
  override val entityId: Long,
  val points: Int
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val statusPoints = StatusPointsSMSGProto.StatusPointsSMSG.newBuilder()
      .setEntityId(entityId)
      .setPoints(points)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompStatusPoints(statusPoints)
      .build()
  }
}
