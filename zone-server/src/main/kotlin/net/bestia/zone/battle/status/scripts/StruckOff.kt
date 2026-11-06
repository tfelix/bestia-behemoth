package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 61 (`STRUCK_OFF`): this courier missed a mail deadline and may take no post work for a
 * day. Like every stored effect, it only runs down while its master is in the world.
 */
@Component
class StruckOff : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    const val DURATION_SECONDS = 24 * 60 * 60.0
  }
}
