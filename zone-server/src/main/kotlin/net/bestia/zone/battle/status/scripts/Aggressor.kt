package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 21 (`AGGRESSOR`): this master harmed a player who had not harmed anyone first. Whoever
 * fights back owes no honor for it; see `AggressorSystem`. Each new attack starts the time again.
 */
@Component
class Aggressor : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    const val DURATION_SECONDS = 180.0
  }
}
