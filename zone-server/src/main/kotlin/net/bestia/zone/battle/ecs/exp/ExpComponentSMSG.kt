package net.bestia.zone.battle.ecs.exp

import net.bestia.bnet.proto.ExpComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class ExpComponentSMSG(
  override val entityId: Long,
  val exp: Int,
  val requiredExpNextLevel: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val expComponent = ExpComponentSMSGProto.ExpComponentSMSG.newBuilder()
      .setExp(exp)
      .setRequiredExpNextLevel(requiredExpNextLevel)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setExp(expComponent))
  }
}