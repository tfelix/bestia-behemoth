package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.status.RemovalReason
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import org.springframework.stereotype.Component

/**
 * The warning before [Stone]: the bearer can still act, and a cure in time saves it. The instance's `amount` is how
 * long the Stone lasts when a curse says so, like Stone Curse's level; 0 leaves Stone its own duration.
 */
@Component
class Stoning : StatusEffectScript {

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  override fun onRemove(context: StatusEffectHookContext, reason: RemovalReason) {
    if (reason == RemovalReason.EXPIRED) {
      val stoneSeconds = context.effect.amount.takeIf { it > 0 }?.toDouble()
      context.applyEffect(context.hostId, StatusEffectId.STONE, context.effect.level, stoneSeconds)
    }
  }
}
