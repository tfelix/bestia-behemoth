package net.bestia.zone.movement.ecs

import net.bestia.bnet.proto.SpeedComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class SpeedSMSG(
  override val entityId: Long,
  val speed: Float,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val speedComp = SpeedComponentSMSGProto.SpeedComponentSMSG.newBuilder()
      .setSpeed(speed)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setSpeed(speedComp))
  }
}