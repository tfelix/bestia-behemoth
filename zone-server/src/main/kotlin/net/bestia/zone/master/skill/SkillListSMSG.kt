package net.bestia.zone.master.skill

import net.bestia.bnet.proto.SkillListSMSGProto
import net.bestia.bnet.proto.StateBatchSmsgProto
import net.bestia.zone.message.EntitySMSG

data class SkillListSMSG(
  override val entityId: Long,
  val skills: List<SkillListEntry>
) : EntitySMSG {

  override fun writeTo(update: StateBatchSmsgProto.EntityUpdate.Builder) {
    val protoSkills = skills.map { skill ->
      SkillListSMSGProto.SkillListEntry.newBuilder()
        .setSkillId(skill.skillId)
        .setLevel(skill.level)
        .setPotencyBand(skill.potencyBand)
        .setTemporary(skill.temporary)
        .build()
    }

    val skillListComponent = SkillListSMSGProto.SkillListSMSG.newBuilder()
      .addAllSkills(protoSkills)
      .build()

    update.addComponents(StateBatchSmsgProto.ComponentDelta.newBuilder().setSkillList(skillListComponent))
  }

  data class SkillListEntry(
    val skillId: Long,
    val level: Int,
    /** [net.bestia.zone.battle.potency.Potency.Band] ordinal + 1 for a learned skill, 0 otherwise. */
    val potencyBand: Int = 0,
    /** Held for one cast, like a Plagiarism copy, rather than learned. */
    val temporary: Boolean = false,
  )
}