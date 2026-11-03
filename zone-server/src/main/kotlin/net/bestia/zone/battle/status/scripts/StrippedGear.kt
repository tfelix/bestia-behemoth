package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Gear a strip skill tore off. Its slot stays locked while this lasts, see `SlotLock`. */
@Component
class StrippedGear : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 15.0
  }
}
