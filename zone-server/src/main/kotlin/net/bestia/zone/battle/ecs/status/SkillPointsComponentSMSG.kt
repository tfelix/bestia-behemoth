package net.bestia.zone.battle.ecs.status

import net.bestia.bnet.proto.SkillPointsSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class SkillPointsComponentSMSG(
  override val entityId: Long,
  val points: Int
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val skillPoints = SkillPointsSMSGProto.SkillPointsSMSG.newBuilder()
      .setPoints(points)

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setSkillPoints(skillPoints))
  }
}
