package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 22 (`DISHONORED`): this master's global honor is below zero. `HonorStandingSystem` renews
 * it while that holds, so it runs out by itself once the honor has healed.
 */
@Component
class Dishonored : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION

  /** Renewed from the honor ledger, which is stored; a stored copy would only be stale. */
  override val isPersisted: Boolean = false

  override val showsCountdown: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    /** Outlasts two of `HonorStandingSystem`'s sweeps. */
    const val DURATION_SECONDS = 25.0
  }
}
