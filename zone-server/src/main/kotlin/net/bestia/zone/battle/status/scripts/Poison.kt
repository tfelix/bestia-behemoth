package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * Wears the bearer down without killing it: a slow drain that stops at the last hit point, no natural healing,
 * and a weakened guard. Killing is what a blow is for.
 */
@Component
class Poison : StatusEffectScript {

  override val tickIntervalSeconds: Float = 2f

  override fun durationSeconds(level: Int): Double {
    return 10.0
  }

  override fun onTick(context: StatusEffectTickContext) {
    val health = context.world.get(context.hostId, Health::class) ?: return
    val drain = (health.max / PERCENT_OF_MAX).coerceAtLeast(1).coerceAtMost(health.current - 1)

    if (drain > 0) {
      context.dealDamage(drain)
    }
  }

  override fun apply(
    world: World,
    entityId: EntityId,
    context: StatusValueRecalcContext,
    level: Int,
    sourceEntityId: EntityId?
  ) {
    context.addPercent(defense = DEFENSE_PERCENT)
    context.blockRegen(hp = true)
  }

  private companion object {
    const val PERCENT_OF_MAX = 100
    const val DEFENSE_PERCENT = -25
  }
}
