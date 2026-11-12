package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.status.StackBehavior
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 204 (`RUWACH`): for 10 s, every second, ends Hide and Cloak on everything within 10 m of
 * the bearer. No Potency contest: the docs say detection resistance does not help against it.
 */
@Component
class RuwachPulse : StatusEffectScript {

  override val stackBehavior: StackBehavior = StackBehavior.REFRESH_DURATION

  override val tickIntervalSeconds: Float = PULSE_SECONDS

  override fun durationSeconds(level: Int): Double = DURATION_SECONDS

  override fun onTick(context: StatusEffectTickContext) {
    val world = context.world
    val centre = world.get(context.hostId, Position::class)?.toVec3L() ?: return

    val hidden = ArrayList<Long>()
    world.query(StatusEffects::class, Position::class).each { id ->
      if (id != context.hostId && get<StatusEffects>().isConcealed() && get<Position>().toVec3L().distance(centre) <= RADIUS_TILES) {
        hidden += id
      }
    }
    for (id in hidden) {
      StatusEffectId.CONCEALMENTS.forEach { context.strip(id, it) }
    }
  }

  private companion object {
    const val PULSE_SECONDS = 1f
    const val DURATION_SECONDS = 10.0
    const val RADIUS_TILES = 10L
  }
}
