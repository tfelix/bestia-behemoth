package net.bestia.zone.breeding

import net.bestia.zone.master.skill.BloodlineStudy
import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import org.springframework.stereotype.Component

/** The two master skills a farm owner breeds with, read off the owner's live skills on the tick. */
@Component
class BreederSkills(
  private val skills: SkillRepository,
  private val bloodlineStudy: BloodlineStudy,
) {

  private val beastfriendId: Long? by lazy { skills.findByIdentifier(SkillId.M_BEASTFRIEND)?.id }

  fun warmUp() {
    beastfriendId
    bloodlineStudy.warmUp()
  }

  fun bloodlineStudyLevel(known: KnownSkills?): Int {
    return bloodlineStudy.levelIn(known)
  }

  fun beastfriendLevel(known: KnownSkills?): Int {
    val id = beastfriendId ?: return 0
    return known?.levelOf(id) ?: 0
  }
}
