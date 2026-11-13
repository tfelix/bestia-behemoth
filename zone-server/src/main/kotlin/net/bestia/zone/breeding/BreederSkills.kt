package net.bestia.zone.breeding

import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.SkillIds
import org.springframework.stereotype.Component

/** The two master skills a farm owner breeds with, read off the owner's live skills on the tick. */
@Component
class BreederSkills(
  private val skillIds: SkillIds,
  private val bloodlineStudy: BloodlineStudy,
) {

  private val beastfriendId: Long? get() = skillIds[SkillId.M_BEASTFRIEND]

  fun bloodlineStudyLevel(known: KnownSkills?): Int {
    return bloodlineStudy.levelIn(known)
  }

  fun beastfriendLevel(known: KnownSkills?): Int {
    val id = beastfriendId ?: return 0
    return known?.levelOf(id) ?: 0
  }
}
