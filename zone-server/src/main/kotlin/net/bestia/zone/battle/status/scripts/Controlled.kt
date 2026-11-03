package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** A wild creature turned against its own kind for a while, see `Hostility`. Its source is the controller. */
@Component
class Controlled : StatusEffectScript {

  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 30.0
  }
}
