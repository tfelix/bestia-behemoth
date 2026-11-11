package net.bestia.zone.item.mining

import net.bestia.zone.skill.SkillId
import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.skill.persistence.SkillRepository
import net.bestia.zone.skill.persistence.findByIdentifier
import org.springframework.stereotype.Service

/** A master's level in Mining. */
@Service
class MiningSkill(
  private val skills: SkillRepository,
) {

  /** Resolved by identifier, because the id in `skills.yml` is content and this is code. */
  private val miningId: Long? by lazy { skills.findByIdentifier(SkillId.M_MINING)?.id }

  /** Resolves the skill id at boot, so the tick never reaches the database for it. */
  fun warmUp() {
    miningId
  }

  fun levelOf(known: KnownSkills?): Int {
    val id = miningId ?: return 0

    return known?.levelOf(id) ?: 0
  }
}
