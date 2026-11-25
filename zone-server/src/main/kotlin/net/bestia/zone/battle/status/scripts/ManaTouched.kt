package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 256 (`MANA_TOUCHED`): a wild bestia bred by a den in an Ominous place. It marks the
 * creature for the client's glow; the levels it was born with already make it stronger.
 */
@Component
class ManaTouched : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }
}
