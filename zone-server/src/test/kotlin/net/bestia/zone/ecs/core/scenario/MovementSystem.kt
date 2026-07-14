package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/**
 * Integrates [Position] from [Velocity] every tick.
 *
 * writes Position, reads Velocity.
 */
@Component
class MovementSystem : System {
  override val schedule = Schedule.EveryTick
  override val reads: ComponentClassSet = setOf(Velocity::class)
  override val writes: ComponentClassSet = setOf(Position::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Velocity::class).each {
      val pos = get<Position>()
      val vel = get<Velocity>()

      if (vel.dx == 0f && vel.dy == 0f) return@each
      pos.x += vel.dx * deltaTime
      pos.y += vel.dy * deltaTime
    }
  }
}