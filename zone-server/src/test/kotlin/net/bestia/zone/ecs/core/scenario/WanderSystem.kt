package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/**
 * Idle-wander AI: periodically picks a new deterministic direction and writes it
 * into [Velocity]. Runs a few times per second, not every tick.
 *
 * writes Velocity, reads Wander -> conflicts with [MovementSystem] (which reads
 * Velocity), so the scheduler puts it in an earlier wave: wander decides, then
 * movement integrates.
 */
@Component
class WanderSystem : System {
  override val schedule = Schedule.EverySeconds(0.05f)
  override val reads: ComponentClassSet = setOf(Wander::class)
  override val writes: ComponentClassSet = setOf(Velocity::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Velocity::class, Wander::class).each { id ->
      val vel = get<Velocity>()
      val wander = get<Wander>()

      wander.step++
      // deterministic pseudo-direction based on entity id + step
      when ((id + wander.step) % 4L) {
        0L -> { vel.dx = 1f; vel.dy = 0f }
        1L -> { vel.dx = 0f; vel.dy = 1f }
        2L -> { vel.dx = -1f; vel.dy = 0f }
        else -> { vel.dx = 0f; vel.dy = -1f }
      }
    }
  }
}