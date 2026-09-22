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
      // Unasserted until a village reported 148%: the footprints were every building's and the area was only
      // the districts', so a settlement its districts did not cover came back over one.
      assertTrue(town.builtShare in 0.0..1.0, "builtShare ${town.builtShare} is not a share")
      assertTrue(town.medianFootprint > 0.0, "settlement ${town.settlement} has a zero median footprint")
      // p90 over p10, so below one means the percentiles came back the wrong way round.
      assertTrue(town.footprintSpread >= 1.0, "footprintSpread ${town.footprintSpread} is inverted")
      assertTrue(town.metresToWideStreet >= 0.0)
    }
  }

  @Test
  fun `the wheel detector reads the side streets that make the wheel`() {
    // The claim the metric is built on, asserted rather than assumed: a world with nothing crosswise in it
    // must measure lower, and nowhere else is it written down that the metric notices. That is how a metric
    // quietly stops reading the world it is pointed at.
    //
    // The control is `branchChance = 0`, which is now the whole of it. It used to also set
    // `crossStreetsPerMainStreet = 0`, because removing only the cross streets did not isolate what this
    // metric reads - a branch leaves its parent at a right angle and is just as crosswise, and with the
    // chords alone removed the measurement moved five settlements up and three down. The cross streets have
    // since been deleted outright, so what is left to remove is the branches, which were the crosswise half
    // all along.
    //
    // With no branches a town is its arteries out of the market and the seeds growing in on the bearings
    // between them - all radial, and the metric should say so.
    //
    // Relative, not absolute: the level depends on how much of a town is core, so the assertion is that
    // crosswise streets move it, not that it sits anywhere in particular.
    val spokesOnly = StandardWorld.build(
      StandardWorld.demoConfig(seed = 909L).copy(widthCells = 160, heightCells = 160),
      params = WorldParams(town = TownParams(streets = StreetParams(branchChance = 0.0)))
    )

    val withSideStreets = median(measured)
    val without = median(TownMetrics.of(spokesOnly))

    assertTrue(
      withSideStreets > without,
      "side streets measured $withSideStreets tangential and spokes alone measured $without - " +
          "the metric did not see them"
    )
  }

  /**
   * Median tangential share over the settlements that actually have suburbs.
   *
   * Hamlets are excluded, and they are two thirds of this world. `tangentialShare` only looks at street
   * beyond `CORE_SHARE` of the tier's footprint radius - which is what makes it a statement about suburbs
   * rather than about the core - and a hamlet's streets do not reach that far, so it reads zero for them by
   * construction. Taking the median over all settlements therefore measured the hamlets, and returned 0.06:
   * a number one rounding away from saying nothing at all, which is what it eventually said.
   */
  private fun median(towns: List<TownMetrics.Measured>): Double {
    val sorted = towns.filter { it.tier <= SettlementTier.VILLAGE }.map { it.tangentialShare }.sorted()
    return sorted[sorted.size / 2]
  }
}
