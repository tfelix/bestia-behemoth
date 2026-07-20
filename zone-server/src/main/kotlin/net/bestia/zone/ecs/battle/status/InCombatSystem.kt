package net.bestia.zone.ecs.battle.status

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component as SpringComponent

/**
 * Ticks down [InCombat.remainingSeconds] and removes the marker once an entity has gone
 * [InCombat.TIMEOUT_SECONDS] without taking further damage, re-enabling HP/Mana regen. Ordered
 * after [net.bestia.zone.ecs.battle.damage.ReceivedDamageSystem] (50), which is what refreshes
 * the timer on a hit.
 */
@SpringComponent
@Order(51)
class InCombatSystem : System {

  override val schedule: Schedule = Schedule.EverySeconds(1f)
  override val writes: ComponentClassSet = setOf(InCombat::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(InCombat::class).each { id ->
      val inCombat = get<InCombat>()
      inCombat.remainingSeconds -= deltaTime
      if (inCombat.remainingSeconds <= 0f) {
        world.remove(id, InCombat::class)
      }
    }
  }
}
