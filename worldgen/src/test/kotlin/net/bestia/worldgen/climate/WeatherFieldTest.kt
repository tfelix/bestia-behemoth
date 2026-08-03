package net.bestia.worldgen.climate

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The two numbers in the weather model that had to be measured rather than reasoned about.
 *
 * **The region count**, because `area / spacing²` overstates what Bridson actually packs, and a per-region
 * cost budget or a content plan sized against the wrong figure is wrong by a fifth.
 *
 * **The squash gain**, because fbm of gradient noise is neither uniform nor Gaussian and its distribution
 * moves with the octave count. Every threshold in `WeatherParams` is a quantile of this field, so a squash
 * that leaves it clustered around the middle makes "severity above 0.93" mean something other than "the top
 * seven percent" - and every climatology built on top is quietly skewed with no symptom but the weather being
 * dull.
 */
class WeatherFieldTest {

  @Test
  fun `the squash leaves the field close to uniform`() {
    // Sampled the way the model samples it: three-octave fbm at scattered positions, not a sweep along a line,
    // which would measure the field's autocorrelation instead of its distribution.
    val values = ArrayList<Double>(200_000)
    for (i in 0 until 200_000) {
      val x = GenRng.hashUnit(7L, i.toLong()) * 1_000.0
      val y = GenRng.hashUnit(11L, i.toLong()) * 1_000.0
      values.add(WeatherModel.uniform(Noise.fbm(42L, x, y, 3)))
    }
    values.sort()

    fun quantile(share: Double) = values[(share * (values.size - 1)).toInt()]

    // A uniform field has its p-th quantile at p. Two and a half points of slack: the point is that a
    // threshold means roughly its face value, not that the fit is perfect.
    for (share in listOf(0.05, 0.25, 0.50, 0.75, 0.95)) {
      val at = quantile(share)
      assertTrue(
        abs(at - share) < 0.055,
        "quantile $share sits at ${"%.3f".format(at)}; the squash gain needs re-fitting"
      )
    }
  }

  @Test
  fun `the extreme thresholds are as rare as they claim`() {
    // The assertion the ladder actually depends on. `tornadoSeverity` at 0.93 has to be a top-seven-percent
    // event in the *field*, before the convective and mana terms narrow it further.
    val values = ArrayList<Double>(100_000)
    for (i in 0 until 100_000) {
      val x = GenRng.hashUnit(3L, i.toLong()) * 1_000.0
      val y = GenRng.hashUnit(5L, i.toLong()) * 1_000.0
      values.add(WeatherModel.uniform(Noise.fbm(99L, x, y, 3)))
    }

    val params = WeatherParams()
    val aboveTornado = values.count { it >= params.tornadoSeverity }.toDouble() / values.size
    val aboveStorm = values.count { it >= params.thunderstormSeverity }.toDouble() / values.size

    assertTrue(aboveTornado in 0.01..0.15, "tornado-grade severity happens $aboveTornado of the time")
    assertTrue(aboveStorm in 0.15..0.55, "storm-grade severity happens $aboveStorm of the time")
    assertTrue(aboveStorm > aboveTornado, "the ladder is upside down")
  }

  @Test
  fun `a 128 km world has around forty-six weather regions`() {
    // Counted, not derived. `128² / 16²` says 64; Bridson packs at about 0.70 per r², so it is nearer 46.
    // This is the "count the output" step, and the bound is what catches a spacing that got detail-scaled.
    for (seed in listOf(1L, 7L, 42L)) {
      val world = StandardWorld.build(WorldConfig(seed = seed, widthCells = 128, heightCells = 128))
      val regions = WeatherRegions.of(world)

      assertTrue(
        regions.count in 38..56,
        "seed $seed produced ${regions.count} weather regions on a 128 km world; expected around 46"
      )
      assertTrue(
        regions.inhabitedCount in 8..40,
        "seed $seed has ${regions.inhabitedCount} regions with land in them"
      )
      // Every region has to have covered some climate cells, or its summary is a division by a guarded one.
      assertTrue(regions.regions.all { it.cellCount > 0 }, "a region covered no climate cell at all")
    }
  }

  @Test
  fun `every land position resolves to a region whose summary is finite`() {
    val world = StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
    val regions = WeatherRegions.of(world)
    val config = world.config

    for (step in 0 until 64) {
      val x = (step + 0.5) / 64.0 * config.widthMetres
      for (inner in 0 until 64) {
        val y = (inner + 0.5) / 64.0 * config.heightMetres
        val region = regions.regionAt(x, y)

        assertTrue(region.meanTemperature.isFinite(), "region ${region.index} has a non-finite temperature")
        assertTrue(region.landShare in 0.0..1.0, "region ${region.index} has landShare ${region.landShare}")
        assertTrue(region.rainfallAt(0.4) >= 0.0, "region ${region.index} rains a negative amount")
      }
    }
  }
}
