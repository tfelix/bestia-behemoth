package net.bestia.zone.component

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.SkillListSMSGProto
import net.bestia.zone.message.EntitySMSG

data class SkillListSMSG(
  override val entityId: Long,
  val skills: List<SkillListEntry>
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoSkills = skills.map { skill ->
      SkillListSMSGProto.SkillListEntry.newBuilder()
        .setAttackId(skill.attackId)
        .setLevel(skill.level)
        .setMaxLevel(skill.maxLevel)
        .setLearned(skill.learned)
        .build()
    }

    val skillListComponent = SkillListSMSGProto.SkillListSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllSkills(protoSkills)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompSkillList(skillListComponent)
      .build()
  }

  data class SkillListEntry(
    val attackId: Long,
    val level: Int,
    val maxLevel: Int,
    val learned: Boolean
  )
}
