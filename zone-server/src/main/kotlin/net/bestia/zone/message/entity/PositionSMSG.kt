package net.bestia.zone.message.entity

import net.bestia.zone.geometry.Vec3L
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PositionComponentProto
import net.bestia.bnet.proto.Vec3OuterClass


data class PositionSMSG(
  override val entityId: Long,
  val position: Vec3L,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val pos = Vec3OuterClass.Vec3.newBuilder()
      .setX(position.x)
      .setY(position.y)
      .setZ(position.z)

    val positionComp = PositionComponentProto.PositionComponent.newBuilder()
      .setEntityId(entityId)
      .setPosition(pos)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompPosition(positionComp)
      .build()
  }
}
