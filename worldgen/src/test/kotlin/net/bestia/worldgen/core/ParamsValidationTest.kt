package net.bestia.worldgen.core

import net.bestia.worldgen.bio.BiomeParams
import net.bestia.worldgen.civ.StreetParams
import net.bestia.worldgen.climate.ClimateParams
import net.bestia.worldgen.geo.ClosedBasinParams
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.DropletParams
import net.bestia.worldgen.geo.ErosionParams
import net.bestia.worldgen.geo.GlacialParams
import net.bestia.worldgen.history.HistoryParams
import net.bestia.worldgen.hydro.HydrologyParams
import net.bestia.worldgen.voxel.StrataParams
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The constraints a params file is checked against, and the ones a file cannot express in its syntax.
 *
 * The single-field bounds are not tested one by one - there are something like two hundred and twenty of them
 * and `require(x > 0.0)` needs no proof. What is worth pinning is the **pairs**, because those are the ones
 * where both values are individually sensible and the world still comes out wrong, silently: soft rock holding
 * the cliffs, an equator colder than the poles, a badlands slope no rock can reach. A file that sets one side of
 * a pair and not the other is the ordinary way to produce them.
 *
 * The other job is NaN. The digest is `Double.toRawBits`, and NaN has many bit patterns, so a NaN reaching a
 * params class could give two runs of the same file two different `pipelineVersion`s. `ParamsText` refuses
 * non-finite input, which covers every field arriving from a file; these bounds are what covers a programmatic
 * caller, because NaN fails every comparison and so fails every `require` here too.
 */
class ParamsValidationTest {

  @Test
  fun `soft rock may not hold a steeper slope than hard rock`() {
    val error = assertFailsWith<IllegalArgumentException> { ErosionParams(talusSoft = 0.8, talusHard = 0.65) }
    assertTrue("talusSoft" in error.message!!, error.message!!)
  }

  @Test
  fun `the poles may not be warmer than the equator`() {
    assertFailsWith<IllegalArgumentException> {
      ClimateParams(equatorTemperature = -10.0, poleTemperature = 20.0)
    }
  }

  @Test
  fun `a badlands slope no rock can reach is refused`() {
    // Cliff is tested first and wins, so badlands steeper than cliff means the biome never appears anywhere -
    // a whole biome silently absent from every world, which no invariant asserts against.
    assertFailsWith<IllegalArgumentException> { BiomeParams(badlandsSlope = 0.6, cliffSlope = 0.45) }
  }

  @Test
  fun `a bed thickness range that runs backwards is refused`() {
    assertFailsWith<IllegalArgumentException> { StrataParams(minBedThickness = 40.0, maxBedThickness = 38.0) }
  }

  @Test
  fun `a war that ends before it starts is refused`() {
    assertFailsWith<IllegalArgumentException> { HistoryParams(minWarYears = 50, maxWarYears = 45) }
  }

  @Test
  fun `a fort range inside its own clearance is refused`() {
    // `SpecialSiteCandidates` rejects anything nearer than the clearance or further than the range, so this
    // pair satisfies neither and the world gets no forts at all - which reads as "the civs never got round
    // to it" rather than as a bad number.
    assertFailsWith<IllegalArgumentException> { HistoryParams(fortClearance = 8_000.0, fortRange = 4_000.0) }
  }

  @Test
  fun `a basin too shallow to be worth carving at its own maximum depth is refused`() {
    assertFailsWith<IllegalArgumentException> { ClosedBasinParams(maxDepth = 6.0, minDepth = 5.0) }
  }

  @Test
  fun `a town with no radial street, or a ring outside the town, is refused`() {
    assertFailsWith<IllegalArgumentException> { StreetParams(minRadials = 9, maxRadials = 7) }
    assertFailsWith<IllegalArgumentException> { StreetParams(rings = listOf(0.28, 1.4)) }
    assertFailsWith<IllegalArgumentException> { StreetParams(rings = listOf(0.0)) }
  }

  @Test
  fun `a droplet cell coarser than its own tile is refused`() {
    // It leaves a one-cell grid with no slope in it, so the pass runs over every chunk in the world and
    // changes nothing - the most expensive possible way to do nothing.
    assertFailsWith<IllegalArgumentException> { DropletParams(tileExtent = 128.0, cellSize = 200.0) }
  }

  @Test
  fun `a trough narrower than its own floor, and a slope band that narrows, are refused`() {
    assertFailsWith<IllegalArgumentException> { GlacialParams(wallSpread = 0.5) }
    assertFailsWith<IllegalArgumentException> { HydrologyParams(channelSlopeRange = 0.5) }
  }

  @Test
  fun `NaN is refused wherever a bound excludes it`() {
    // NaN fails every comparison, so `require(x > 0.0)` rejects it without saying so. Worth a test because it
    // is the reason the bounds were completed at all: a NaN tunable would fingerprint differently between two
    // runs of the same file and move `pipelineVersion` on a boot.
    assertFailsWith<IllegalArgumentException> { DetailParams(wavelength = Double.NaN) }
    assertFailsWith<IllegalArgumentException> { ErosionParams(erodibility = Double.NaN) }
    assertFailsWith<IllegalArgumentException> { ClimateParams(equatorTemperature = Double.NaN) }
    assertFailsWith<IllegalArgumentException> { GlacialParams(snowlineTemperature = Double.NaN) }
  }

  @Test
  fun `the defaults satisfy their own constraints`() {
    // Trivial to state and the thing that would actually break: every `require` above runs on construction, so
    // a bound written slightly too tight makes the whole generator unusable rather than one test red. The
    // constructions are the assertion.
    ErosionParams(); ClimateParams(); BiomeParams(); StrataParams(); HistoryParams(); ClosedBasinParams()
    StreetParams(); DropletParams(); GlacialParams(); HydrologyParams(); DetailParams()
  }
}
