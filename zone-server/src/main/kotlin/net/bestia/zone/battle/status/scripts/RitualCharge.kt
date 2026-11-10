package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `RITUAL_CHARGE`: what a finished ritual leaves on its leader for one minute. [net.bestia.zone.battle.ecs.effects.ActiveStatusEffect.amount]
 * holds the Potency bonus, `count` the success-roll bonus in percent; the leader's next skill spends both.
 */
@Component
class RitualCharge : StatusEffectScript {

  /** The charge is for this login's next skill; a relog lets it go, as the leader's death does. */
  override val isPersisted: Boolean = false

  /** Spent by casting, never dropped by hand: the circle that made it is gone. */
  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return CHARGE_SECONDS
  }

  companion object {
    const val CHARGE_SECONDS = 60.0
  }
}
