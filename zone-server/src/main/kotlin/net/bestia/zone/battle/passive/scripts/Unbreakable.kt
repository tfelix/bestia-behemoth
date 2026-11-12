package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Unbreakable (`skills.yml` id 142): once per 10 min, 6 min at Lv.3, a blow that would kill the Brawler leaves it at
 * 1 HP. See `StatusEffectService.survivesLethalDamage`.
 */
@Component
class Unbreakable : PassiveSkillScript {

  override val skill = SkillId.M_UNBREAKABLE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.lastStandCooldownSeconds = cooldownSeconds(level)
  }

  companion object {
    private const val MAX_LEVEL = 3

    fun cooldownSeconds(level: Int): Int {
      return if (level >= MAX_LEVEL) 360 else 600
    }
  }
}
