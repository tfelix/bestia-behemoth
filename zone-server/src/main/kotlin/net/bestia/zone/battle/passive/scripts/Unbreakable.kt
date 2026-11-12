package net.bestia.zone.battle.passive.scripts

import net.bestia.zone.battle.passive.PassiveSkillScript
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.skill.SkillId
import org.springframework.stereotype.Component

/**
 * Unbreakable (`skills.yml` id 142): once per 10 min, 6 min at Lv.3, a blow that would kill the Brawler leaves it at
 * 1 HP. See `StatusEffectService.survivesLethalDamage`. From Lv.2 a stun and a knockback hit it half as hard while it
 * is above half its HP.
 */
@Component
class Unbreakable : PassiveSkillScript {

  override val skill = SkillId.M_UNBREAKABLE

  override fun apply(context: StatusValueRecalcContext, level: Int) {
    context.lastStandCooldownSeconds = cooldownSeconds(level)
    if (level >= STEADY_FROM_LEVEL) {
      context.halvesStunAndKnockback = true
    }
  }

  companion object {
    private const val MAX_LEVEL = 3
    private const val STEADY_FROM_LEVEL = 2

    fun cooldownSeconds(level: Int): Int {
      return if (level >= MAX_LEVEL) 360 else 600
    }
  }
}
