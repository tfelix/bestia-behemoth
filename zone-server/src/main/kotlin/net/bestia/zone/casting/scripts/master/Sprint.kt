package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component

/**
 * Sprint (`skills.yml` id 46): a burst of speed for five seconds, paid in stamina rather than mana. The SPRINTING
 * status carries the speed and ends early on an attack or a cast.
 */
@Component
class Sprint : SkillStrategy {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    return SkillLevelCost.of(skill).copy(manaCost = 0, staminaCost = STAMINA_COST)
  }

  override fun isCastPossible(ctx: SkillContext): Boolean = true

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.SPRINTING.id, ctx.skillLevel)

    return null
  }

  private companion object {
    const val STAMINA_COST = 20
  }
}
