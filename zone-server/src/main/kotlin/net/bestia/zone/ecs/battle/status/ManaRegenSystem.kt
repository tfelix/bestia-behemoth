package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent
import kotlin.math.roundToInt

/**
 * Passive Mana regeneration for every entity with [Mana], gated on the entity not being
 * [InCombat]. Amount follows `MaxSP * INT / 99 + 3 / 40` per tick.
 */
@SpringComponent
@Order(57)
class ManaRegenSystem : System {

  override val schedule: Schedule = Schedule.EverySeconds(8f)
  override val reads: ComponentClassSet = setOf(StatusValues::class, InCombat::class)
  override val writes: ComponentClassSet = setOf(Mana::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Mana::class).each { id ->
      if (world.has(id, InCombat::class)) return@each

      val mana = get<Mana>()
      if (mana.current >= mana.max) return@each

      val intelligence = world.get(id, StatusValues::class)?.intelligence ?: 0
      val regen = (mana.max * intelligence / 99.0 + 3.0 / 40.0).roundToInt()
      mana.current += regen
    }
  }
}
