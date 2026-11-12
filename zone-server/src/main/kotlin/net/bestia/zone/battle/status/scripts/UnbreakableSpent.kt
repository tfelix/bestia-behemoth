package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.passive.scripts.Unbreakable
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 229 (`UNBREAKABLE_SPENT`): the cooldown of the Brawler's Unbreakable, which sets how long
 * it lasts. A stored effect, so a relog does not reset it.
 */
@Component
class UnbreakableSpent : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.IGNORE_IF_PRESENT

  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Unbreakable.cooldownSeconds(level).toDouble()
  }
}
