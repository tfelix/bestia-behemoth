package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 10 (`WARDED`): keeps the bearer and other players from harming each other. [WardAura]
 * refreshes it while the bearer stays in the field.
 */
@Component
class Warded : StatusEffectScript {

  override val shield: HarmShield = HarmShield.PLAYERS

  /** Once the bearer leaves the field it runs out within seconds, so a stored copy is never worth restoring. */
  override val isPersisted: Boolean = false

  /** The aura renews it every pulse; its seconds are only how long it outlasts the field. */
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    /** Outlasts two of [WardAura]'s pulses, so one late pulse leaves no gap in the protection. */
    const val DURATION_SECONDS = 5.0
  }
}
