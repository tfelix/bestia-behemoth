package net.bestia.zone.casting.essence

import net.bestia.zone.battle.damage.HitForm
import net.bestia.zone.battle.damage.HitKind
import net.bestia.zone.battle.ecs.status.CombatStats
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.ecs.core.World
import net.bestia.zone.skill.persistence.Skill
import net.bestia.zone.util.EntityId

/**
 * What the headgear boss essences take off a skill's cost: Pharaoh's Crown a share of the mana, Nine Tails the mana and
 * the cast bar of every ninth spell. Asked when the cast starts and again when it is paid for, so both agree.
 */
object EssenceSkillCost {

  fun of(world: World, casterId: EntityId, skill: Skill, cost: SkillLevelCost): SkillLevelCost {
    val values = world.get(casterId, CombatStats::class)?.values ?: return cost
    if (values.everyNinthSpellFree && isSpell(skill) && NineTails.isReady(world, casterId)) {
      return cost.copy(manaCost = 0, castTimeSeconds = 0f)
    }

    val cut = values.manaCostCutPercent.coerceAtMost(100)
    return if (cut > 0) cost.copy(manaCost = cost.manaCost * (100 - cut) / 100) else cost
  }

  fun isSpell(skill: Skill): Boolean = HitKind.ofSkill(skill).form != HitForm.PHYSICAL
}
