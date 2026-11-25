package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** `NINE_TAILS_READY`: the next spell is instant and free. It waits for that spell. */
@Component
class NineTailsReady : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT
  override val isCancellable: Boolean = false
  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 3600.0
  }
}
