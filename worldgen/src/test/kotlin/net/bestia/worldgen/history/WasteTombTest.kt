package net.bestia.worldgen.history

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.EventKind
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.SiteKind
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Graves out in the harsh country, and the log line that explains each of them.
 *
 * The point of the pass under test is that a player crossing a desert can find something that is not a monster.
 * Before it, every tomb in every world sat 320 m from a settlement - because `buryFigure` discards
 * `Person.slainAt` and buries by home town - and settlements are placed by habitability, so the waste held
 * nothing but wounds.
 *
 * ### Why 256 cells and why this seed
 *
 * A waste tomb needs the conjunction of harsh ground, a settlement within `seerRange` of it, and an explorer or
 * general who lived long enough to be rolled - so it is seed-dependent in the way `SpecialSitesTest` documents
 * for forts and lighthouses.
 *
 * It ran on the *default* seed until the resource and town stages were reversioned, which reseeds everything
 * downstream of them and left that world with no lost traveller at all. **The pass was fine**: a sweep of
 * twenty-five consecutive seeds at 256 cells found waste tombs on twenty-three of them, three to thirteen
 * apiece, and exactly two produced none - the old default being one of the two. So this is re-pinned rather
 * than relaxed, which is what the module's own habit asks for: pin an existence check to a seed that has the
 * thing, rather than writing a conditional that passes vacuously on every seed that does not.
 *
 * [SEED] is the richest of that sweep at thirteen, chosen for margin - a seed with one waste tomb would fail
 * this file again on the next reseed of anything upstream.
 */
class WasteTombTest {

  private companion object {
    /** See the class KDoc. Not the demo default, which produces none since the stage reversioning. */
    const val SEED = 11_753_243L

    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig().copy(seed = SEED, widthCells = 256, heightCells = 256)
    )

    val chronicle get() = world.world.chronicle

    val biome: IntLayer by lazy { world.world.layers.require(LayerId.BIOME) }

    /** The biomes `SpecialSites.WASTE_HARSHNESS` admits. Duplicated so a change there has to be deliberate. */
    val WASTE = setOf(Biome.DESERT, Biome.ICE_SHEET, Biome.COLD_DESERT, Biome.BADLANDS)

    fun biomeAt(x: Double, y: Double) = Biome.entries.getOrNull(biome.sampleNearest(x, y))

    /**
     * Slack for the jitter `loseTravellers` applies off the candidate position, in metres.
     *
     * Mirrors `HistorySim.TOMB_OFFSET`, which is private, and duplicating it rather than opening it up is the
     * right trade here: this is a *bound* on the assertion rather than the value under test, so a change over
     * there should make somebody look at this line rather than silently widen it.
     */
    const val TOMB_OFFSET_ALLOWANCE = 320.0

    /** Tombs with no home settlement: exactly what `loseTravellers` produces and `buryFigure` never does. */
    val loneTombs by lazy {
      chronicle.sites.filter { it.kind == SiteKind.TOMB && it.settlement < 0 }
    }
  }

  @Test
  fun `the world has waste for somebody to be lost in`() {
    // The premise. If this fails the biome retuning moved and the assertions below are vacuous rather than
    // passing - the same distinction `AetheriteOutcropTest` opens with.
    var found = 0
    for (y in 0 until biome.region.height step 4) {
      for (x in 0 until biome.region.width step 4) {
        val kind = Biome.entries.getOrNull(biome[biome.region.minX + x, biome.region.minY + y]) ?: continue
        if (kind in WASTE) found++
      }
    }
    assertTrue(found > 0, "no harsh ground anywhere on the reference world; nothing can be lost in it")
  }

  @Test
  fun `somebody is lost in the waste and buried where they fell`() {
    val lost = chronicle.events.count { it.kind == EventKind.TRAVELLER_LOST }
    assertTrue(
      lost > 0,
      "no TRAVELLER_LOST on the reference world; either the waste candidates are empty or " +
          "TRAVELLER_LOSS_CHANCE is too low for the pass to fire at all"
    )

    // The event and the grave are two halves of one thing, and a pass that logged without burying would leave a
    // chronicle line pointing at nothing - which is the failure mode `SpecialSitesTest` calls out for events.
    assertTrue(
      loneTombs.isNotEmpty(),
      "$lost travellers were lost and no tomb was raised away from a settlement"
    )
  }

  @Test
  fun `a waste tomb stands in the waste`() {
    // The property that makes the feature worth having: a barrow explained by a death in the desert has to
    // actually be in the desert. A tomb `offset` off its candidate can land in a neighbouring cell, so this
    // allows the ring of biomes around harsh ground rather than demanding the exact cell.
    for (tomb in loneTombs) {
      val here = biomeAt(tomb.position.x, tomb.position.y)
      val metres = biome.region.resolution.metresPerCell
      val nearby = (-1..1).any { dy ->
        (-1..1).any { dx ->
          biomeAt(tomb.position.x + dx * metres, tomb.position.y + dy * metres) in WASTE
        }
      }
      assertTrue(
        nearby,
        "a lone tomb at (${tomb.position.x.toInt()}, ${tomb.position.y.toInt()}) is in $here with no harsh " +
            "ground in the surrounding cells; it was not placed by loseTravellers"
      )
    }
  }

  @Test
  fun `a waste tomb is out of town and above water`() {
    val settlements = world.world.features.all()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
    val elevation: FloatLayer = world.world.layers.require(LayerId.ELEVATION)

    for (tomb in loneTombs) {
      val ground = elevation.sampleBilinear(tomb.position.x, tomb.position.y)
      assertTrue(
        ground > world.config.seaLevel,
        "a lone tomb stands at ${ground.toInt()} m, below the sea"
      )

      // The near limit `wastes` applies, minus the offset the burial adds. A grave beside a town is a grave
      // somebody would have found, which is the whole reason the candidate scan has a near edge at all.
      val nearest = settlements.minOfOrNull { it.position.distanceTo(tomb.position) } ?: continue
      assertTrue(
        nearest > HistoryParams().monasteryClearance - TOMB_OFFSET_ALLOWANCE,
        "a lone tomb is ${nearest.toInt()} m from a settlement, inside the clearance the waste scan requires"
      )
    }
  }

  @Test
  fun `every lost traveller has a name and a civilisation in the log`() {
    // What makes the grave findable from the other end: `chronicle -Pquests` mines events, so a barrow whose
    // event names nobody is a barrow no quest can be built from.
    for (event in chronicle.events.filter { it.kind == EventKind.TRAVELLER_LOST }) {
      assertTrue(
        event.actors.any { it.index >= 0 },
        "a TRAVELLER_LOST event in year ${event.year} names no actor"
      )
      assertTrue(
        event.detail.isNotBlank(),
        "a TRAVELLER_LOST event in year ${event.year} has no text"
      )
    }
  }
}
