package net.bestia.zone.casting

import net.bestia.bnet.proto.DetectedEntitiesSmsgProto
import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.SMSG
import net.bestia.zone.util.EntityId

/** What a Detect skill found, for the caster's map only. Account-scoped like a dialog, see the proto. */
data class DetectedEntitiesSMSG(
  val entities: List<Detected>,
  val seconds: Float,
) : SMSG {

  data class Detected(val entityId: EntityId, val position: Vec3L, val isMaster: Boolean)

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val message = DetectedEntitiesSmsgProto.DetectedEntitiesSMSG.newBuilder().setSeconds(seconds)
    entities.forEach { detected ->
      message.addEntities(
        DetectedEntitiesSmsgProto.DetectedEntity.newBuilder()
          .setEntityId(detected.entityId)
          .setPosition(Vec3OuterClass.Vec3.newBuilder().setX(detected.position.x).setY(detected.position.y).setZ(detected.position.z))
          .setMaster(detected.isMaster)
      )
    }

    return EnvelopeProto.Envelope.newBuilder().setDetectedEntities(message).build()
  }
}
