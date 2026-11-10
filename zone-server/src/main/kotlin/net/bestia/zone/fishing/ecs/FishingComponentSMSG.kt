package net.bestia.zone.fishing.ecs

import net.bestia.bnet.proto.FishingComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

data class FishingComponentSMSG(
  override val entityId: Long,
  val bobber: Vec3L,
  val biting: Boolean,
  val removed: Boolean,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val fishing = FishingComponentSMSGProto.FishingComponentSMSG.newBuilder()
      .setBobber(Vec3OuterClass.Vec3.newBuilder().setX(bobber.x).setY(bobber.y).setZ(bobber.z))
      .setBiting(biting)
      .setRemoved(removed)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setFishing(fishing))
  }
}
