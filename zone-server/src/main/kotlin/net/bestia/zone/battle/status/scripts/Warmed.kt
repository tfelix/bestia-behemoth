package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Registered under `status_effects.yml` id 19 (`WARMED`), from a bestia's Warm Fur. It changes no value; while it is
 * on, `EnvironmentalExposureSystem` widens the comfort band.
 */
@Component
class Warmed : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return BASE_SECONDS + SECONDS_PER_LEVEL * level
  }

  private companion object {
    const val BASE_SECONDS = 300.0
    const val SECONDS_PER_LEVEL = 60.0
  }
}
