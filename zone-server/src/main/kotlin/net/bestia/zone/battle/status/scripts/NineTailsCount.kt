package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** `NINE_TAILS_COUNT`: the spells cast since the last free one, in its count. An hour without a spell starts over. */
@Component
class NineTailsCount : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT
  override val isCancellable: Boolean = false
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 3600.0
  }
}
