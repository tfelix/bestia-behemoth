package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.HealthComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class HealthComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val healthComponent = HealthComponentSMSGProto.HealthComponentSMSG.newBuilder()
      .setCurrent(current)
      .setMax(max)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setHealth(healthComponent))
  }
}