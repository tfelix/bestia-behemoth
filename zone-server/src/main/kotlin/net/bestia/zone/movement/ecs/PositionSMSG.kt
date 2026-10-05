package net.bestia.zone.movement.ecs

import net.bestia.bnet.proto.PositionComponentProto
import net.bestia.bnet.proto.Vec3OuterClass
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.EntitySMSG

data class PositionSMSG(
  override val entityId: Long,
  val position: Vec3L,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val pos = Vec3OuterClass.Vec3.newBuilder()
      .setX(position.x)
      .setY(position.y)
      .setZ(position.z)

    val positionComp = PositionComponentProto.PositionComponent.newBuilder()
      .setPosition(pos)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setPosition(positionComp))
  }
}