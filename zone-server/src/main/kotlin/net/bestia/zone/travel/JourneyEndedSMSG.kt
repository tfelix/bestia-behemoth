package net.bestia.zone.travel

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.JourneyEndedSMSGProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

data class JourneyEndedSMSG(
  val entityId: EntityId,
  val failure: JourneyFailure?,
  val position: Vec3L,
  val name: String,
) : SMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val ended = JourneyEndedSMSGProto.JourneyEndedSMSG.newBuilder()
      .setEntityId(entityId)
      .setFailure(wireFailure())
      .setPosition(Vec3OuterClass.Vec3.newBuilder().setX(position.x).setY(position.y).setZ(position.z))
      .setName(name)

    return EnvelopeProto.Envelope.newBuilder()
      .setJourneyEnded(ended)
      .build()
  }

  private fun wireFailure(): JourneyEndedSMSGProto.JourneyEndedSMSG.Failure {
    return when (failure) {
      null -> JourneyEndedSMSGProto.JourneyEndedSMSG.Failure.NONE
      JourneyFailure.NO_ROUTE -> JourneyEndedSMSGProto.JourneyEndedSMSG.Failure.NO_ROUTE
      JourneyFailure.STUCK -> JourneyEndedSMSGProto.JourneyEndedSMSG.Failure.STUCK
      JourneyFailure.DIED -> JourneyEndedSMSGProto.JourneyEndedSMSG.Failure.DIED
    }
  }
}
