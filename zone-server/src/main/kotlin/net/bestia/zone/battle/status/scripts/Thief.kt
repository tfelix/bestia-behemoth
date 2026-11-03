package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 23 (`THIEF`): this master stole within the last six real hours (18 Bestia hours, as
 * pvp.md has it). Killing them is worth extra honor. Each theft starts the time again.
 */
@Component
class Thief : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION

  override fun durationSeconds(level: Int): Double {
    return DURATION_SECONDS
  }

  private companion object {
    const val DURATION_SECONDS = 6 * 60 * 60.0
  }
}
