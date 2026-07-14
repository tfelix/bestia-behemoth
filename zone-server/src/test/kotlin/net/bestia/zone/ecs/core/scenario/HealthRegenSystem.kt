package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent

/**
 * Slow health regeneration — the archetypal "runs once every few seconds"
 * system. writes Health only, so it is independent of movement/wander and can
 * share a parallel wave with [MovementSystem].
 */
@SpringComponent
class HealthRegenSystem : System {
  override val schedule = Schedule.EverySeconds(0.1f)
  override val writes: ComponentClassSet = setOf(Health::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Health::class).each {
      val hp = get<Health>()
      if (hp.value < hp.max) {
        hp.value = minOf(hp.max, hp.value + 1)
      }
    }
  }
}
