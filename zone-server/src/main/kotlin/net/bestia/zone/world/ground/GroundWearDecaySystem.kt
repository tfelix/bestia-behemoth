package net.bestia.zone.world.ground

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * Lets walked ground grow back over, and writes out what has changed.
 *
 * ### It sweeps the worn columns, not the world
 *
 * The loop is over `GroundWearRegistry.wornKeys()`, which is bounded by how much ground is *held and worn*
 * right now - never by the size of the world. `ScorchRegrowthSystem`'s argument, and the reason neither needs
 * a per-tick budget where `WorldObjectResidencySystem` does.
 *
 * ### Every tick, but the sweep is once a minute
 *
 * The tick is for draining columns read off the database, which land on another thread and have to be merged
 * in where the map is safe to touch. That queue is empty on almost every tick and costs a poll to find out.
 *
 * The sweep itself keeps its own accumulator. A column ages from a stored timestamp rather than by a fixed
 * step per pass, so running it more often would not fade paths faster - it would only notice sooner, against a
 * fade measured in Bestia days.
 *
 * ### It declares no components, and means it
 *
 * This touches the wear registry, the clock and `GroundOverlayService`, none of which is a component. An
 * earlier version of `ScorchRegrowthSystem` declared a write it did not make purely to force an ordering, and
 * that flattened wave scheduling across the engine and ran the test suite's heap out. Do not repeat it.
 */
@Component
class GroundWearDecaySystem(
  private val registry: GroundWearRegistry,
  private val overlay: GroundOverlayService,
  private val config: GroundWearConfig,
  private val clock: BestiaClock,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  private var secondsSinceSweep = 0f
  private var secondsSinceFlush = 0f

  override fun update(world: World, deltaTime: Float) {
    val now = clock.now().absoluteSecond

    // A column just read in is ground the player has been looking at clean since they arrived, so it is
    // announced the moment it lands rather than waiting for the next sweep.
    registry.drainLoaded(now).forEach { overlay.markLayersDirty(it) }

    secondsSinceSweep += deltaTime
    if (secondsSinceSweep < SWEEP_SECONDS) return
    secondsSinceSweep = 0f

    secondsSinceFlush += SWEEP_SECONDS
    val flushing = secondsSinceFlush >= config.flushIntervalSeconds
    if (flushing) secondsSinceFlush = 0f

    val keys = registry.wornKeys()
    if (keys.isEmpty() && !flushing) return

    var faded = 0
    var closed = 0

    for (columnKey in keys) {
      val column = registry.wearOf(columnKey) ?: continue

      if (!column.ageTo(now, config.fadeSeconds)) continue

      // Re-announce either way: a client holding this column is drawing a path that just got fainter, and the
      // message carries the whole column so one settles it.
      overlay.markLayersDirty(columnKey)

      if (column.isEmpty) closed++ else faded++
    }

    val written = if (flushing) registry.flushDirty() else 0

    if (faded > 0 || closed > 0 || written > 0) {
      LOG.debug {
        "ground wear: $faded column(s) faded, $closed grown over, $written written, " +
            "${registry.wornColumns} held"
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SWEEP_SECONDS = 60f
  }
}
