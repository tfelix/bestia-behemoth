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
 * and a weakened guard. Killing is what a blow is for. A brewed poison sets its own drain as the effect's amount,
 * in per mille of the maximum HP; any other drains [DEFAULT_DRAIN_PER_MILLE].
 */
@Component
class Poison : StatusEffectScript {

  override val tickIntervalSeconds: Float = 2f

  override fun durationSeconds(level: Int): Double {
    return 30.0
  }

  override fun onTick(context: StatusEffectTickContext) {
    val health = context.world.get(context.hostId, Health::class) ?: return
    val perMille = context.effect.amount.takeIf { it > 0 } ?: DEFAULT_DRAIN_PER_MILLE
    val drain = (health.max * perMille / 1000).coerceAtLeast(1).coerceAtMost(health.current - 1)

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

  companion object {
    const val DEFAULT_DRAIN_PER_MILLE = 10
    private const val DEFENSE_PERCENT = -25
  }
}
