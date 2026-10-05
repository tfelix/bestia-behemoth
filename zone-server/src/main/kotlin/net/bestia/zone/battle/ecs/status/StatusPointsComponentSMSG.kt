package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.StatusPointsSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class StatusPointsComponentSMSG(
  override val entityId: Long,
  val points: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val statusPoints = StatusPointsSMSGProto.StatusPointsSMSG.newBuilder()
      .setPoints(points)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setStatusPoints(statusPoints))
  }
}
