package net.bestia.zone.casting.portal

import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.bnet.proto.WarpMarksComponentSMSGProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

data class WarpMarksComponentSMSG(
  override val entityId: Long,
  val spots: List<Vec3L>,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val marks = WarpMarksComponentSMSGProto.WarpMarksComponentSMSG.newBuilder()
    spots.forEach { marks.addSpots(Vec3OuterClass.Vec3.newBuilder().setX(it.x).setY(it.y).setZ(it.z)) }

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setWarpMarks(marks))
  }
}
