package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.HarmShield
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 254 (`CAPTURE_GRACE`): a just captured Empyrite cannot be harmed for a while. The applier
 * sets how long; [durationSeconds] only stands in when it does not.
 */
@Component
class CaptureGrace : StatusEffectScript {

  override val shield: HarmShield = HarmShield.GRACE

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DEFAULT_SECONDS
  }

  private companion object {
    const val DEFAULT_SECONDS = 600.0
  }
}
