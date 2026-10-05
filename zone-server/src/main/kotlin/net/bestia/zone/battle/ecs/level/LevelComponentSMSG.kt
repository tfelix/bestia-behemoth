package net.bestia.zone.battle.ecs.level

import net.bestia.bnet.proto.LevelComponentSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class LevelComponentSMSG(
  override val entityId: Long,
  val level: Int,
) : EntitySMSG {
  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val levelComponent = LevelComponentSMSGProto.LevelComponentSMSG.newBuilder()
      .setLevel(level)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setLevel(levelComponent))
  }
}
