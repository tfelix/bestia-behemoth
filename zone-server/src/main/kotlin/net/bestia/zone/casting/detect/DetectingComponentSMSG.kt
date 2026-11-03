package net.bestia.zone.casting.detect

import net.bestia.bnet.proto.DetectingComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.message.EntitySMSG

data class DetectingComponentSMSG(
  override val entityId: Long,
  val found: List<Detecting.Detected>,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val detecting = DetectingComponentSMSGProto.DetectingComponentSMSG.newBuilder().setRemoved(removed)
    found.forEach {
      detecting.addEntities(
        DetectingComponentSMSGProto.DetectedEntity.newBuilder()
          .setEntityId(it.entityId)
          .setPosition(Vec3OuterClass.Vec3.newBuilder().setX(it.position.x).setY(it.position.y).setZ(it.position.z))
          .setMaster(it.isMaster)
      )
    }

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setDetecting(detecting))
  }
}
