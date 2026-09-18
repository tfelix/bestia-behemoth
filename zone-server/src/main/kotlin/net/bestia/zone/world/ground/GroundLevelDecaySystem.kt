package net.bestia.zone.world.ground

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import net.bestia.zone.environment.time.BestiaClock
import org.springframework.stereotype.Component

/**
 * Lets marked ground go back to being ground, and writes out what has changed.
 *
 * ### It sweeps the marked columns, not the world
 *
 * The loop is over each store's own keys, which is bounded by how much ground is *held and marked* right now -
 * never by the size of the world. `ScorchRegrowthSystem`'s argument, and the reason neither needs a per-tick
 * budget where `WorldObjectResidencySystem` does.
 *
 * ### Every tick, but the sweep is once a minute
 *
 * The tick is for draining columns read off the database, which land on another thread and have to be merged
 * in where the map is safe to touch. That queue is empty on almost every tick and costs a poll to find out.
 *
 * The sweep itself keeps its own accumulator. A column ages from a stored timestamp rather than by a fixed
 * step per pass, so running it more often would not fade anything faster - it would only notice sooner,
 * against fades measured in Bestia days.
 *
 * ### One pass for every graded layer
 *
 * The stores are collected rather than named. Worn ground fades over days and blood over longer, but the fade
 * *duration* is each store's own number and the pass is identical, so a new layer is a bean rather than a
 * second system that would have to be kept in step with this one.
 *
 * ### It declares no components, and means it
 *
 * This touches the stores, the clock and [GroundOverlayService], none of which is a component. An earlier
 * version of `ScorchRegrowthSystem` declared a write it did not make purely to force an ordering, and that
 * flattened wave scheduling across the engine and ran the test suite's heap out. Do not repeat it.
 */
@Component
class GroundLevelDecaySystem(
  private val stores: List<GroundLevelStore>,
  private val overlay: GroundOverlayService,
  private val clock: BestiaClock,
) : System {

  override val schedule: Schedule = Schedule.EveryTick

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  private var secondsSinceSweep = 0f

  /** Per store, because two layers may be written out on different intervals. */
  private val secondsSinceFlush = HashMap<GroundLayer, Float>()

  override fun update(world: World, deltaTime: Float) {
    val now = clock.now().absoluteSecond

    // A column just read in is ground the player has been looking at clean since they arrived, so it is
    // announced the moment it lands rather than waiting for the next sweep.
    stores.forEach { store ->
      store.drainLoaded(now).forEach { overlay.markLayersDirty(it) }
    }

    secondsSinceSweep += deltaTime
    if (secondsSinceSweep < SWEEP_SECONDS) return
    secondsSinceSweep = 0f

    stores.forEach { sweep(it, now) }
  }

  private fun sweep(store: GroundLevelStore, nowSecond: Long) {
    val flushing = flushDue(store)

    val keys = store.markedKeys()
    if (keys.isEmpty() && !flushing) return

    var faded = 0
    var cleared = 0

    for (columnKey in keys) {
      val column = store.columnAt(columnKey) ?: continue

      if (!column.ageTo(nowSecond, store.fadeSeconds)) continue

      // Re-announce either way: a client holding this column is drawing a mark that just got fainter, and the
      // message carries the whole column so one settles it.
      overlay.markLayersDirty(columnKey)

      if (column.isEmpty) cleared++ else faded++
    }

    val written = if (flushing) store.flushDirty() else 0

    if (faded > 0 || cleared > 0 || written > 0) {
      LOG.debug {
        "${store.layer}: $faded column(s) faded, $cleared cleared, $written written, " +
            "${store.markedColumns} held"
      }
    }
  }

  private fun flushDue(store: GroundLevelStore): Boolean {
    val elapsed = secondsSinceFlush.getOrDefault(store.layer, 0f) + SWEEP_SECONDS
    val due = elapsed >= store.flushIntervalSeconds

    secondsSinceFlush[store.layer] = if (due) 0f else elapsed

    return due
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SWEEP_SECONDS = 60f
  }
}
