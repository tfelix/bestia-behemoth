package net.bestia.zone.ground

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.world.time.BestiaClock
import org.springframework.stereotype.Component
import net.bestia.zone.world.stream.ChunkStreamSystem

/**
 * Lets tracks fade.
 *
 * ### Every tick, unlike wear's sweep
 *
 * Prints last minutes where a path lasts days, and `SpoorService` reads them with no age filter - so a sweep
 * once a minute would let a tracker follow prints that should be gone. The pass is over `GroundStampRegistry`'s
 * own keys, which is bounded by `GroundStampConfig.maxColumns` and never by the size of the world.
 *
 * ### Ahead of the chunk stream
 *
 * A player arriving this tick is sent the column's prints by `GroundOverlayService`, which does not filter by
 * age. Sweeping first means that snapshot never carries a print that has already expired.
 *
 * ### It declares no components, and means it
 *
 * The registry and the clock are neither of them components, so there is nothing for
 * `SystemScheduler.conflicts()` to order this against; the order above is about observable sequence and is
 * declared with `before`.
 */
@Component
class GroundStampSystem(
  private val registry: GroundStampRegistry,
  private val clock: BestiaClock,
) : System {
  override val phase = Phase.WORLD
  override val before = setOf(ChunkStreamSystem::class)

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    registry.sweep(clock.now().absoluteSecond)
  }
}
