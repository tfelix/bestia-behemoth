package net.bestia.zone.ecs.core.scenario

import net.bestia.zone.ecs.core.Component
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component as SpringComponent
import kotlin.reflect.KClass

/**
 * Idle-wander AI: periodically picks a new deterministic direction and writes it
 * into [Velocity]. Runs a few times per second, not every tick.
 *
 * writes Velocity, reads Wander -> conflicts with [MovementSystem] (which reads
 * Velocity), so the scheduler puts it in an earlier wave: wander decides, then
 * movement integrates.
 */
@SpringComponent
class WanderSystem : System {
  override val schedule = Schedule.EverySeconds(0.05f)
  override val reads: Set<KClass<out Component>> = setOf(Wander::class)
  override val writes: Set<KClass<out Component>> = setOf(Velocity::class)

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

/**
 * Integrates [Position] from [Velocity] every tick, marks moved entities as
 * changed (for outbound component sync) and emits an [EntityMoved] event.
 *
 * writes Position, reads Velocity.
 */
@SpringComponent
class MovementSystem : System {
  override val schedule = Schedule.EveryTick
  override val reads: Set<KClass<out Component>> = setOf(Velocity::class)
  override val writes: Set<KClass<out Component>> = setOf(Position::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Position::class, Velocity::class).each { id ->
      val pos = get<Position>()
      val vel = get<Velocity>()

      if (vel.dx == 0f && vel.dy == 0f) return@each
      pos.x += vel.dx * deltaTime
      pos.y += vel.dy * deltaTime
      world.markChanged<Position>(id)
      world.emit(EntityMoved(id, pos.x, pos.y))
    }
  }
}

/**
 * Slow health regeneration — the archetypal "runs once every few seconds"
 * system. writes Health only, so it is independent of movement/wander and can
 * share a parallel wave with [MovementSystem].
 */
@SpringComponent
class HealthRegenSystem : System {
  override val schedule = Schedule.EverySeconds(0.1f)
  override val writes: Set<KClass<out Component>> = setOf(Health::class)

  override fun update(world: World, deltaTime: Float) {
    world.query(Health::class).each { id ->
      val hp = get<Health>()
      if (hp.value < hp.max) {
        hp.value = minOf(hp.max, hp.value + 1)
        world.markChanged<Health>(id)
      }
    }
  }
}
