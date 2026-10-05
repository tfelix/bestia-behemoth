package net.bestia.zone.world.ground

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.fire.GroundFireSystem
import net.bestia.zone.world.prop.WorldObjectResidencySystem
import org.springframework.stereotype.Component

/**
 * Flushes the ground overlay once per tick, behind the terrain it describes.
 *
 * After `ChunkStreamSystem` has served the chunk payloads and `WorldObjectResidencySystem` has announced what
 * stands on them, so a client is told about the ground, then the things on it, then what has happened to it -
 * in that order, within one tick.
 *
 * Declares no `reads` and no `writes`, honestly: it touches the scorch registry and the socket, neither of
 * which is an ECS component. Its `after` is therefore about *observable* sequence, not about a shared
 * component.
 */
@Component
class GroundOverlaySystem(
  private val overlay: GroundOverlayService,
) : System {
  override val phase = Phase.WORLD
  override val after = setOf(GroundStampSystem::class, GroundFireSystem::class, WorldObjectResidencySystem::class)

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    if (overlay.pending == 0) return
    overlay.flush()
  }
}
