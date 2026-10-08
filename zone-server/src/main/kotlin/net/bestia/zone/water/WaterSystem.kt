package net.bestia.zone.water

import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.stream.ChunkStreamSystem
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/** Moves water ten times a second, before the stream system so holders hear of a commit in the same tick. */
@Component
class WaterSystem(
  private val water: WaterService,
) : System {

  override val phase = Phase.WORLD

  override val schedule: Schedule = Schedule.EveryTicks(2)

  override val before: Set<KClass<out System>> = setOf(ChunkStreamSystem::class)

  override fun update(world: World, deltaTime: Float) {
    if (water.isIdle) return
    water.step(deltaTime)
  }
}
