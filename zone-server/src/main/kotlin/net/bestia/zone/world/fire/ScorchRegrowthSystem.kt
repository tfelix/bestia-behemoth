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
 * ### Once a minute
 *
 * `EverySeconds(60)`: a scar heals over Bestia *days*, so a faster cadence would re-integrate the same hour
 * repeatedly to move nothing.
 *
 * ### It declares no components, and that is now true rather than merely convenient
 *
 * This touches the scorch registry, the weather field and `GroundOverlayService`, none of which is a component.
 * An earlier version declared a write it did not make - on a component `ChunkStreamSystem` reads - purely to
 * force an ordering, because `markDirty` used to read the subscription service. That worked and cost far too
 * much: an always-present system conflicting with a large part of the engine flattens the wave scheduling for
 * everything and ran this suite's heap out. `GroundOverlayService` keeps its own holders map instead; see its
 * KDoc.
 */
@Component
class ScorchRegrowthSystem(
  private val registry: ScorchRegistry,
  private val rain: RainAccumulator,
  private val overlay: GroundOverlayService,
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

      // Re-announce it either way: a client holding this column is drawing a scar that just got smaller, and
      // the overlay carries the whole mask so one message settles it.
      overlay.markDirty(columnKey)

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
