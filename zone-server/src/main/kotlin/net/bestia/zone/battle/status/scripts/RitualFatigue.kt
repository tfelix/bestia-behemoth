package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** `RITUAL_FATIGUE`: everyone who joined a ritual rests for five minutes and may not join or lead another. */
@Component
class RitualFatigue : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return FATIGUE_SECONDS
  }

  companion object {
    const val FATIGUE_SECONDS = 300.0
  }
}
