package net.bestia.zone.world.ground

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component

/**
 * Lets tracks fade, and decides which columns of them go on the wire this tick.
 *
 * ### Every tick, unlike wear's sweep
 *
 * Prints last minutes where a path lasts days, and the pacing they are announced at is measured in a second -
 * so a sweep once a minute would both hold prints past their life and batch them into visible clumps. The pass
 * is over `GroundStampRegistry`'s own keys, which is bounded by `GroundStampConfig.maxColumns` and never by the
 * size of the world.
 *
 * ### `@Order(44)`, ahead of the chunk stream
 *
 * `MoveSystem` (40) lays the prints, this notices them, and `GroundOverlaySystem` (47) sends them - all inside
 * the tick they were made in. Being ahead of `ChunkStreamSystem` (45) also means a player arriving this tick is
 * served the column's terrain after the prints on it were already accounted for, so they are told once rather
 * than twice.
 *
 * ### It declares no components, and means it
 *
 * The registry, the clock and [GroundOverlayService] are none of them components, so there is nothing for
 * `SystemScheduler.conflicts()` to order this against and the `@Order` above is about observable sequence only.
 * Declaring a write it does not make to force an ordering is the trap `ScorchRegrowthSystem` already fell into.
 */
@Component
@Order(44)
class GroundStampSystem(
  private val registry: GroundStampRegistry,
  private val overlay: GroundOverlayService,
  private val clock: BestiaClock,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val announced = registry.sweep(clock.now().absoluteSecond)
    if (announced.isEmpty()) return

    announced.forEach { overlay.markStampsDirty(it) }

    LOG.trace { "ground stamps: ${announced.size} column(s) announced, ${registry.stampedColumns} held" }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
