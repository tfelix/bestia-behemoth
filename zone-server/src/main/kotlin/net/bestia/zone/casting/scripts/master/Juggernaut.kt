package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component

/** Juggernaut (`skills.yml` id 139): the Knight takes on [StatusEffectId.JUGGERNAUT]. Cools down 120 s, 90 s at Lv.3. */
@Component
class Juggernaut : SkillStrategy {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    return SkillLevelCost.of(skill).copy(cooldownSeconds = cooldownSeconds(level))
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.applyStatusEffect(ctx.casterId, StatusEffectId.JUGGERNAUT.id, level = ctx.skillLevel.coerceIn(1, MAX_LEVEL))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 3

    fun cooldownSeconds(level: Int): Float {
      return if (level >= MAX_LEVEL) 90f else 120f
    }
  }
}
