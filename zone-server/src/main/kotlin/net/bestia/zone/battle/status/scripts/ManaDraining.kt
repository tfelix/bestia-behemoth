package net.bestia.zone.battle.status.scripts

import net.bestia.zone.battle.ecs.status.Mana
import net.bestia.zone.battle.status.StatusEffectScript
import net.bestia.zone.battle.status.StatusEffectTickContext
import net.bestia.zone.aoi.EntityAOIService
import net.bestia.zone.movement.ecs.Position
import org.springframework.stereotype.Component

/**
 * `status_effects.yml` id 207 (`MANA_DRAINING`): the Sage's Mana Drain. Every second, each foe within `2 m/lv` loses
 * `1%/lv` of its max mana to the bearer, for `10 s/lv`.
 */
@Component
class ManaDraining(private val aoi: EntityAOIService) : StatusEffectScript {

  override val isPersisted: Boolean = false

  override val tickIntervalSeconds: Float = PULSE_SECONDS

  override fun durationSeconds(level: Int): Double = SECONDS_PER_LEVEL * level

  override fun onTick(context: StatusEffectTickContext) {
    val world = context.world
    val centre = world.get(context.hostId, Position::class)?.toVec3L() ?: return
    val own = world.get(context.hostId, Mana::class) ?: return
    val radius = RADIUS_TILES_PER_LEVEL * context.level

    for (id in aoi.queryEntitiesInCube(centre, radius * 2)) {
      val at = world.get(id, Position::class)?.toVec3L() ?: continue
      if (at.distance(centre) > radius || !AuraScript.isFoeOf(world, context.hostId, id)) continue
      val pool = world.get(id, Mana::class) ?: continue

      val drained = minOf(pool.current, pool.max * PERCENT_PER_LEVEL * context.level / 100)
      pool.current -= drained
      own.current += drained
    }
  }

  private companion object {
    const val PULSE_SECONDS = 1f
    const val SECONDS_PER_LEVEL = 10.0
    const val RADIUS_TILES_PER_LEVEL = 2L
    const val PERCENT_PER_LEVEL = 1
  }
}
