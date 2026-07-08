package net.bestia.zone.component

import net.bestia.bnet.proto.EnvelopeProto
import net.bestia.bnet.proto.LearnedSkillsSMSGProto
import net.bestia.zone.message.entity.EntitySMSG

data class LearnedSkillsSMSG(
  override val entityId: Long,
  val skills: List<SkillEntry>
) : EntitySMSG {

  override fun toBnetEnvelope(): EnvelopeProto.Envelope {
    val protoSkills = skills.map { skill ->
      LearnedSkillsSMSGProto.SkillEntry.newBuilder()
        .setAttackId(skill.attackId)
        .setLevel(skill.level)
        .build()
    }

    val learnedSkills = LearnedSkillsSMSGProto.LearnedSkillsSMSG.newBuilder()
      .setEntityId(entityId)
      .addAllSkills(protoSkills)
      .build()

    return EnvelopeProto.Envelope.newBuilder()
      .setCompLearnedSkills(learnedSkills)
      .build()
  }

  data class SkillEntry(
    val attackId: Long,
    val level: Int
  )
}
