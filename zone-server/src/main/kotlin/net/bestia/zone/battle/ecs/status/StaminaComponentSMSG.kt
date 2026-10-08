package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.StaminaComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class StaminaComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val staminaComponent = StaminaComponentSMSGProto.StaminaComponentSMSG.newBuilder()
      .setCurrent(current)
      .setMax(max)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setStamina(staminaComponent))
  }
}
