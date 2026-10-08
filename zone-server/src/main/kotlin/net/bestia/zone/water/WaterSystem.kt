package net.bestia.zone.water

import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.stream.ChunkStreamSystem
import org.springframework.stereotype.Component
import kotlin.reflect.KClass

/** Puts requested water into the world on the tick, before the stream system so holders hear of it this tick. */
@Component
class WaterSystem(
  private val water: WaterService,
) : System {

  override val phase = Phase.WORLD

  override val before: Set<KClass<out System>> = setOf(ChunkStreamSystem::class)

  override fun update(world: World, deltaTime: Float) {
    if (water.isIdle) return
    water.drainPours()
  }
}
