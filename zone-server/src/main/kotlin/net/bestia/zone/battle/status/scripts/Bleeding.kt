package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.EntityAction
import net.bestia.zone.battle.status.StatusEffectHookContext
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.movement.ecs.Path
import net.bestia.zone.movement.ecs.Speed
import org.springframework.stereotype.Component

/**
 * A wound that opens whenever the bearer exerts itself: every tile walked and every swing or cast costs health,
 * and it can kill. Standing still lets it close twice as fast, and any heal closes it at once (see `Healing`).
 */
@Component
class Bleeding : StatusEffectScript {

  override val tickIntervalSeconds: Float = 1f

  override fun durationSeconds(level: Int): Double {
    return 30.0
  }

  override fun onTick(context: StatusEffectTickContext) {
    val walking = context.world.has(context.hostId, Path::class)
    if (!walking) {
      context.effect.remainingSeconds -= 1f
      return
    }

    // One beat of walking covers about as many tiles as the walker's speed.
    val tiles = context.world.get(context.hostId, Speed::class)?.speed ?: 1f
    context.dealDamage((woundOf(context.world.get(context.hostId, Health::class)) * tiles).toInt())
  }

  override fun onActed(context: StatusEffectHookContext, action: EntityAction) {
    context.dealDamage(woundOf(context.world.get(context.hostId, Health::class)).toInt())
  }

  private fun woundOf(health: Health?): Float {
    return ((health?.max ?: 0) * WOUND_PERCENT / 100f).coerceAtLeast(1f)
  }

  private companion object {
    const val WOUND_PERCENT = 2
  }
}
