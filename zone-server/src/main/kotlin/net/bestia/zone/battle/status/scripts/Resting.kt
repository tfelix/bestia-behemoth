package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * `RESTING`: sitting, lying down or asleep, the only time stamina comes back by itself. Held while the pose lasts;
 * see `net.bestia.zone.ai.ecs.RestingSystem`.
 */
@Component
class Resting : StatusEffectScript {

  override val isPersisted: Boolean = false
  override val showsCountdown: Boolean = false

  // Standing up is how a player ends it; a right-click on the icon would only be undone a second later.
  override val isCancellable: Boolean = false

  override fun durationSeconds(level: Int): Double {
    return Double.POSITIVE_INFINITY
  }
}
