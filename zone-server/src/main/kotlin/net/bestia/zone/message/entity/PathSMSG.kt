package net.bestia.zone.message.entity

import net.bestia.zone.geometry.Vec3L
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.PathComponentSMSGProto
import net.bestia.bnet.proto.Vec3OuterClass

data class PathSMSG(
  override val entityId: Long,
  val path: List<Vec3L>,
) : EntitySMSG {
  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val pathBuilder = mutableListOf<Vec3OuterClass.Vec3>()

    path.forEach { vec ->
      val pos = Vec3OuterClass.Vec3.newBuilder()
        .setX(vec.x)
        .setY(vec.y)
        .setZ(vec.z)
        .build()
      pathBuilder.add(pos)
    }

    val pathComp = PathComponentSMSGProto.PathComponentSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllPath(pathBuilder)

    return EnvelopeProto.Envelope.newBuilder()
      .setCompPath(pathComp)
      .build()
  }
}
