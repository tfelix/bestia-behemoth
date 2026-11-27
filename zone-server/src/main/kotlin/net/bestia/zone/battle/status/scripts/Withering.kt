package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Health
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.battle.status.StatusValueRecalcContext
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 261 (`WITHERING`): standing on land the Withering corrupted. A slow drain that stops at the
 * last hit point, no natural healing, and less attack; level 2 on the worst ground drains and weakens twice as much.
 * Exposure renews it while the bearer stays; it fades soon after they leave.
 */
@Component
class Withering : StatusEffectScript {

  override val isPersisted: Boolean = false

  override val tickIntervalSeconds: Float = 5f

  override fun durationSeconds(level: Int): Double {
    return LINGER_SECONDS
  }

  override fun onTick(context: StatusEffectTickContext) {
    val health = context.world.get(context.hostId, Health::class) ?: return
    val drain = (health.max * DRAIN_PER_MILLE * context.level / 1000)
      .coerceAtLeast(1)
      .coerceAtMost(health.current - 1)

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
    context.addPercent(attack = ATTACK_PERCENT_PER_LEVEL * level)
    context.blockRegen(hp = true)
  }

  private companion object {
    const val LINGER_SECONDS = 20.0
    const val DRAIN_PER_MILLE = 15
    const val ATTACK_PERCENT_PER_LEVEL = -10
  }
}
