package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * Inside a wind ward: every ranged physical hit misses, see `PhysicalAttackStrategy`. Its zone refreshes it like
 * [Quagmired]'s.
 */
@Component
class Sheltered : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 2.0
  }
}
