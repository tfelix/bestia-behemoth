package net.bestia.zone.world.fire

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Flushes the ground overlay once per tick, behind the terrain it describes.
 *
 * `@Order(47)`: after `ChunkStreamSystem` (45) has served the chunk payloads and
 * `WorldObjectResidencySystem` (46) has announced what stands on them, so a client is told about the ground,
 * then the things on it, then what has happened to it - in that order, within one tick.
 *
 * Declares no `reads` and no `writes`, honestly: it touches the scorch registry and the socket, neither of
 * which is an ECS component, so there is nothing for `SystemScheduler.conflicts()` to order it against. The
 * `@Order` above is therefore about *observable* sequence rather than about wave scheduling, which is worth
 * saying because the two are easy to conflate.
 */
@Component
@Order(47)
class GroundOverlaySystem(
  private val overlay: GroundOverlayService,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    if (overlay.pending == 0) return
    overlay.flush()
  }
}
