package net.bestia.worldgen.civ

import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.pipeline.WorldParams
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The layout metrics: the instrument every later street change is judged with.
 *
 * Tested against a real world for `DistrictTest`'s reason - every claim here is about the relationship between
 * a town's streets and its buildings, and a fixture would have to invent both sides of it.
 *
 * What is asserted is that each number is *in the range its own meaning allows*, and that the one the street
 * work exists to move actually moves when the thing it measures does. A metric nobody has watched respond is
 * a number, not an instrument.
 */
class TownMetricsTest {

  private val generated: GeneratedWorld by lazy {
    StandardWorld.build(StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160))
  }

  private val measured by lazy { TownMetrics.of(generated) }

  @Test
  fun `the world has towns to measure at all`() {
    // Habit six, and it is load-bearing here: every test below iterates this list.
    assertTrue(measured.isNotEmpty(), "no town in the world was measured")
    assertTrue(measured.any { it.tier <= SettlementTier.TOWN }, "no town big enough to have a patched core")
  }

  @Test
  fun `every measured town is internally consistent`() {
    for (town in measured) {
      assertTrue(town.buildings > 0, "settlement ${town.settlement} was measured with no buildings")
      assertTrue(town.streetMetres > 0.0, "settlement ${town.settlement} has buildings and no street")
      assertTrue(town.wideShare in 0.0..1.0, "wideShare ${town.wideShare} is not a share")
      assertTrue(town.tangentialShare in 0.0..1.0, "tangentialShare ${town.tangentialShare} is not a share")
      assertTrue(town.medianFootprint > 0.0, "settlement ${town.settlement} has a zero median footprint")
      // p90 over p10, so below one means the percentiles came back the wrong way round.
      assertTrue(town.footprintSpread >= 1.0, "footprintSpread ${town.footprintSpread} is inverted")
      assertTrue(town.metresToWideStreet >= 0.0)
    }
  }

  @Test
  fun `the wheel detector reads the cross streets that make the wheel`() {
    // The claim the metric is built on, asserted rather than assumed: one world, one seed, one number changed.
    // A cross street is the crosswise part of a town - the radials are spokes - so a town built without any
    // must measure lower, and nowhere else is it written down that the metric notices. That is how a metric
    // quietly stops reading the world it is pointed at.
    //
    // Relative, not absolute: the level depends on how much of a town is core, so the assertion is that cross
    // streets move it, not that it sits anywhere in particular. See `TownMetrics.Measured.tangentialShare`.
    val spokesOnly = StandardWorld.build(
      StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160),
      params = WorldParams(town = TownParams(streets = StreetParams(crossStreetsPerMainStreet = 0)))
    )

    val withCrossStreets = median(measured)
    val without = median(TownMetrics.of(spokesOnly))

    assertTrue(
      withCrossStreets > without,
      "cross streets measured $withCrossStreets tangential and spokes alone measured $without - " +
          "the metric did not see them"
    )
  }

  private fun median(towns: List<TownMetrics.Measured>): Double {
    val sorted = towns.map { it.tangentialShare }.sorted()
    return sorted[sorted.size / 2]
  }
}
