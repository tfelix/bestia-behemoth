package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 224 (`ENCHANT_POISON`): for 5 min every swing that lands may poison its target, `5%/lv` of
 * the time. The level is Enchant Poison's; the amount is the poison's drain, see [Poison]. `PoisonedBlade` rolls it.
 */
@Component
class PoisonedWeapon : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  companion object {
    private const val DURATION_SECONDS = 300.0
    private const val MAX_LEVEL = 5

    fun chancePercent(level: Int): Int {
      return 5 * level.coerceIn(1, MAX_LEVEL)
    }
  }
}
