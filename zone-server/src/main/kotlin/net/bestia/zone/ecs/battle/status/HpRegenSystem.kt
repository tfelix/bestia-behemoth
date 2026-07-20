package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.roundToInt

/**
 * Passive HP regeneration for every entity with [Health], gated on the entity not being
 * [InCombat]. Amount follows `MaxHP * VIT / 99 + 2 / 100` per tick.
 */
@SpringComponent
@Order(56)
class HpRegenSystem : System {

  override val schedule: Schedule = Schedule.EverySeconds(6f)
  override val reads: ComponentClassSet = setOf(Attributes::class, InCombat::class)
  override val writes: ComponentClassSet = setOf(Health::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Health::class).each { id ->
      if (world.has(id, InCombat::class)) return@each

      val health = get<Health>()
      if (health.current >= health.max) return@each

      val vitality = world.get(id, Attributes::class)?.vitality ?: 0
      val regen = (health.max * vitality / 99.0 + 2.0 / 100.0).roundToInt()
      health.current += regen
    }
  }
}
