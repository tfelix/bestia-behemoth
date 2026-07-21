package net.bestia.zone.skill

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
        .setSkillId(skill.skillId)
        .setLevel(skill.level)
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
    val skillId: Long,
    val level: Int,
  )
}