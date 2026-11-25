package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/** `TIDEWALL_SPENT`: the Leviathan's essence swallowed a blow; the next one lands until this runs out. */
@Component
class TidewallSpent : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override val isCancellable: Boolean = false

  /** Ids do not outlive a relog anyway, and ten seconds are not worth keeping. */
  override val isPersisted: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }
}
