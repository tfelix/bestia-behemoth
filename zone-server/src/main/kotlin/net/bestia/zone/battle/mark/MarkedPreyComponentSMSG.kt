package net.bestia.zone.battle.mark

import net.bestia.bnet.proto.MarkedPreyComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.util.EntityId

data class MarkedPreyComponentSMSG(
  override val entityId: Long,
  val preyId: EntityId?,
  val position: Vec3L?,
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val marked = MarkedPreyComponentSMSGProto.MarkedPreyComponentSMSG.newBuilder().setMarked(preyId != null && position != null)
    if (preyId != null && position != null) {
      marked.setPreyEntityId(preyId)
        .setPosition(Vec3OuterClass.Vec3.newBuilder().setX(position.x).setY(position.y).setZ(position.z))
    }

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setMarkedPrey(marked))
  }
}
