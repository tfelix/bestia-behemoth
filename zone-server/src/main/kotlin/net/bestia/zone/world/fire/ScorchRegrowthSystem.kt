package net.bestia.zone.world.fire

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Schedule
import net.bestia.zone.ecs.core.System
import net.bestia.zone.ecs.core.World
import org.springframework.stereotype.Component

/**
 * Greens burnt ground back over as rain falls on it.
 *
 * ### It sweeps the scars, not the world
 *
 * The loop is over `ScorchRegistry.scarredKeys()`, which is bounded by how much ground is burnt *right now* -
 * a handful of columns in ordinary play, zero most of the time. Nothing here scales with world size, which is
 * what makes a whole-set sweep affordable where `WorldObjectResidencySystem` needs a per-tick budget.
 *
 * ### Once a minute, and touching no components at all
 *
 * `EverySeconds(60)`: a scar heals over Bestia *days*, so a faster cadence would re-integrate the same hour
 * repeatedly to move nothing. Declaring no `reads` and no `writes` is honest rather than lazy - this reads the
 * scorch registry and the weather field, neither of which is an ECS component, so there is nothing for
 * `SystemScheduler.conflicts()` to order it against and it may share a wave with anything.
 *
 * The [World] parameter is unused for the same reason, and that is worth a word: every other system in this
 * package touches entities, so a reader meeting one that does not will look for the trick.
 */
@Component
class ScorchRegrowthSystem(
  private val registry: ScorchRegistry,
  private val rain: RainAccumulator,
) : System {

  override val schedule: Schedule = Schedule.EverySeconds(SWEEP_SECONDS)

  override val reads: ComponentClassSet = emptySet()

  override val writes: ComponentClassSet = emptySet()

  override fun update(world: World, deltaTime: Float) {
    val keys = registry.scarredKeys()
    if (keys.isEmpty()) return

    var healed = 0
    var shrunk = 0

    for (columnKey in keys) {
      val scar = registry.scarOf(columnKey) ?: continue

      rain.advance(columnKey, scar)

      // Erosion steps, not a fade: a scar shrinks inward from its edges, so its own shape says which parts of
      // it are edge and no per-cell clock is needed. See ColumnMask.eroded.
      //
      // Always against the *stored* mask, which is why `erodeTo` takes an absolute step count rather than
      // eroding by one. Applying a step to the previous result would compound and heal a scar in minutes.
      val progress = scar.rainMm / rain.healRainMm
      val steps = (progress * MAX_ERODE_STEPS).toInt()

      val before = scar.visible.count
      scar.erodeTo(steps)
      if (scar.visible.count == before) continue

      if (scar.visible.isEmpty) {
        registry.forget(columnKey)
        healed++
      } else {
        shrunk++
      }
    }

    if (healed > 0 || shrunk > 0) {
      LOG.debug {
        "ground scorch: $shrunk column(s) shrank, $healed healed away, ${registry.scarredColumns} still scarred"
      }
    }
  }

  private companion object {
    val LOG = KotlinLogging.logger { }

    const val SWEEP_SECONDS = 60f

    /**
     * Erosion steps at full progress.
     *
     * Six, so a burn up to twelve cells across heals completely and a wider one is left with a shrinking core
     * that the next sweep takes further - the erosion is recomputed from the *stored* mask each pass, so
     * progress past 1.0 keeps eating rather than stalling.
     */
    const val MAX_ERODE_STEPS = 6
  }
}
