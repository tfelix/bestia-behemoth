package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Gear a strip skill tore off. Its slot stays locked while this lasts, see `SlotLock`. */
@Component
class StrippedGear : StatusEffectScript {

  /** Strip Equipment's table: 5 s a level. */
  override fun durationSeconds(level: Int): Double {
    return 5.0 * level
  }
}
