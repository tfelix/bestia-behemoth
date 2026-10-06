package net.bestia.zone.battle.skill

import net.bestia.zone.skill.ecs.KnownSkills
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Answers "may this entity cast this skill at this level" off the live [KnownSkills] component, for callers
 * that only have a skill id and no lock-held scope of their own.
 *
 * Keyed on the catalogue id rather than a [net.bestia.zone.skill.SkillId] constant: the component is keyed on
 * the id, and a constant carries no number to key with - a caller that names a skill in code resolves it
 * through `SkillRepository.findByIdentifier` first, the way [net.bestia.zone.skill.BasicSkillGate]
 * does.
 *
 * There is deliberately nothing here for a *basic attack*: a sword swing has no catalogue row, so there is
 * no such thing as knowing one.
 */
@Service
class SkillCheckService(
  private val world: WorldView
) {

  fun knowsSkill(entityId: EntityId, skillId: Long, minLevel: Int): Boolean {
    // An unlearned skill reads as level 0, so a level below 1 would pass for any skill in the catalogue.
    if (minLevel < 1) {
      return false
    }

    return world.read {
      get(entityId, KnownSkills::class)?.knowsSkill(skillId, minLevel) ?: false
    }
  }
}
