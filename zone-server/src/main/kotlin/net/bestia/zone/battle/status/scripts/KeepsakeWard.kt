package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component
import kotlin.Double.Companion.POSITIVE_INFINITY

/** `KEEPSAKE_WARD`: waits on a bestia until it falls, then `BestiaBagDropSystem` uses it up to send the bag home. */
@Component
class KeepsakeWard : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT
  override val isCancellable: Boolean = false
  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return POSITIVE_INFINITY
  }
}
