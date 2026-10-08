package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.ManaComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class ManaComponentSMSG(
  override val entityId: Long,
  val current: Int,
  val max: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val manaComponent = ManaComponentSMSGProto.ManaComponentSMSG.newBuilder()
      .setCurrent(current)
      .setMax(max)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setMana(manaComponent))
  }
}