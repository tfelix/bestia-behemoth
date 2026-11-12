package net.bestia.zone.casting.scripts.master

import net.bestia.zone.battle.damage.Damage
import net.bestia.zone.casting.SkillContext
import net.bestia.zone.casting.SkillLevelCost
import net.bestia.zone.casting.SkillStrategy
import net.bestia.zone.skill.persistence.Skill
import org.springframework.stereotype.Component

/**
 * Second Wind (`skills.yml` id 141): the Brawler gets back half its stamina, and from Lv.2 a fifth of its HP. Cools
 * down 180 s, 120 s at Lv.3.
 */
@Component
class SecondWind : SkillStrategy {

  override fun costOf(skill: Skill, level: Int): SkillLevelCost {
    return SkillLevelCost.of(skill).copy(cooldownSeconds = cooldownSeconds(level))
  }

  override fun isCastPossible(ctx: SkillContext): Boolean {
    return true
  }

  override fun execute(ctx: SkillContext): Damage? {
    ctx.world.restoreCaster(staminaPercent = STAMINA_PERCENT, healthPercent = healthPercent(ctx.skillLevel))

    return null
  }

  companion object {
    private const val MAX_LEVEL = 3
    private const val STAMINA_PERCENT = 50
    private const val HEALTH_PERCENT = 20
    private const val HEALS_FROM_LEVEL = 2

    fun healthPercent(level: Int): Int {
      return if (level >= HEALS_FROM_LEVEL) HEALTH_PERCENT else 0
    }

    fun cooldownSeconds(level: Int): Float {
      return if (level >= MAX_LEVEL) 120f else 180f
    }
  }
}
