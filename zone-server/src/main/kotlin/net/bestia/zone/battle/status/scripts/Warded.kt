package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 10 (`WARDED`): the catalogue's `shield: PLAYERS` keeps the bearer and other players
 * from harming each other. [WardAura] refreshes it while the bearer stays in the field.
 */
@Component
class Warded : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    /** Outlasts two of [WardAura]'s pulses, so one late pulse leaves no gap in the protection. */
    const val DURATION_SECONDS = 5.0
  }
}
