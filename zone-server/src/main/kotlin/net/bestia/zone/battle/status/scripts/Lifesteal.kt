package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Heals the bearer by half of the damage it deals, see `StatusEffectService.onDamageDealt`. */
@Component
class Lifesteal : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  companion object {
    const val HEAL_PERCENT_OF_DAMAGE = 50
  }
}
