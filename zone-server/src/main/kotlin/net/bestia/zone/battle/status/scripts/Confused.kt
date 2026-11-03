package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** Walks the wrong way: `MoveEntityHandler` sends each move request to a random tile nearby. */
@Component
class Confused : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 5.0
  }
}
