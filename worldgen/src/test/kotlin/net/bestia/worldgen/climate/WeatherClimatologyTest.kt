package net.bestia.worldgen.climate

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * A whole Bestia year of weather, tabulated, and then checked against the climate it came out of.
 *
 * The phase gate for the weather field, and the only thing that can catch the failure that matters: the model
 * runs, produces plausible-looking kinds, and has **no relationship to the world**. A desert that rains a third
 * of the year and a rainforest that does not would both pass every unit test in the module, and neither is
 * visible in a screenshot of one day.
 *
 * The wet-day probability comes from each region's own seasonal rainfall, so these assertions are really about
 * the `rainRateMmPerDay` calibration - which is the one constant in `WeatherParams` that turns millimetres into
 * a probability, and therefore the one that decides whether the whole model means anything.
 */
class WeatherClimatologyTest {

  private val world: GeneratedWorld by lazy {
    StandardWorld.build(WorldConfig(seed = 7L, widthCells = 128, heightCells = 128))
  }

  private val model: WeatherModel by lazy { WeatherModel.of(world) }

  private val temperature: LocalTemperature by lazy {
    LocalTemperature.from(world.world.layers, world.config)
      ?: error("no climate layers on this world")
  }

  /** One year of weather in one region, at a six-Bestia-hour step. */
  private fun year(region: WeatherRegion): List<WeatherState> {
    val out = ArrayList<WeatherState>()
    var day = 0.0
    while (day < WeatherModel.DAYS_PER_YEAR) {
      val yearProgress = day / WeatherModel.DAYS_PER_YEAR
      val air = temperature.at(
        region.centre.x, region.centre.y, region.meanElevation, yearProgress, day - Math.floor(day)
      ).airCelsius
      out.add(model.at(region, day, air))
      day += 0.25
    }
    return out
  }

  private fun wetFraction(states: List<WeatherState>) =
    states.count { it.kind.precipitating }.toDouble() / states.size

  /** The dominant biome of a region, for deciding what it should be doing. */
  private fun biomeOf(region: WeatherRegion): Biome {
    val biome = world.world.layers.require<IntLayer>(LayerId.BIOME)
    return Biome.entries[biome.sampleNearest(region.centre.x, region.centre.y)]
  }

  @Test
  fun `the census is not empty and prints what a year holds`() {
    val census = HashMap<WeatherKind, Int>()
    var samples = 0

    for (region in model.regions.regions) {
      if (region.landShare <= 0.0) continue
      for (state in year(region)) {
        census.merge(state.kind, 1, Int::plus)
        samples++
      }
    }

    println(
      "weather census over ${model.regions.inhabitedCount} land regions, $samples samples:\n  " +
          WeatherKind.entries.joinToString("\n  ") { kind ->
            val n = census[kind] ?: 0
            "%-14s %6d  %5.2f%%".format(kind.label, n, n * 100.0 / samples)
          }
    )

    assertTrue(samples > 0, "no land region on the world; the census tested nothing")
    // Two kinds every world must have, or the ordinary path is broken rather than merely untuned.
    assertTrue((census[WeatherKind.CLEAR] ?: 0) > 0, "it is never clear anywhere, all year")
    assertTrue((census[WeatherKind.RAIN] ?: 0) > 0, "it never rains anywhere, all year")
  }

  @Test
  fun `a region rains in proportion to how much rain it actually gets`() {
    // **Against each region's own rainfall, not against its biome label**, and the first version of this test
    // got that wrong. It asserted "a rainforest is wet more than a fifth of the year" and failed at 0.148 -
    // but the region the classifier had labelled tropical rainforest gets 412 mm a quarter on this world while
    // a region labelled temperate forest gets 585. The model was following the climate faithfully; the
    // expectation was reading a label as though it were a rainfall figure.
    //
    // What is actually being asserted is the property that makes the model *mean* something: wetter ground
    // rains more often, monotonically, with no threshold anybody has to believe.
    data class Sample(val mmPerDay: Double, val wet: Double, val biome: Biome)

    val samples = ArrayList<Sample>()
    for (region in model.regions.regions) {
      if (region.landShare < 0.5) continue
      val wet = wetFraction(year(region))
      val mm = region.seasonalPrecipitation.average() / WeatherRegion.DAYS_PER_QUARTER
      samples.add(Sample(mm, wet, biomeOf(region)))
      if (biomeOf(region) in DIAGNOSED) {
        println(
          "  %-24s mm/day %5.1f  pWet %.3f  measured wet %.3f".format(
            biomeOf(region).label, mm, model.wetDayProbability(region, 0.4), wet
          )
        )
      }
    }

    assertTrue(samples.size >= 4, "only ${samples.size} land regions; the correlation would mean nothing")

    // Spearman: rank the regions by rainfall and by wet fraction, and require the two orders to agree
    // strongly. A model unconnected to the climate scores near zero here whatever its absolute rates.
    val byRain = samples.sortedBy { it.mmPerDay }
    val rainRank = samples.associateWith { byRain.indexOf(it) }
    val byWet = samples.sortedBy { it.wet }
    val wetRank = samples.associateWith { byWet.indexOf(it) }

    val n = samples.size
    val sumSquaredGaps = samples.sumOf { val d = (rainRank[it]!! - wetRank[it]!!).toDouble(); d * d }
    val rho = 1.0 - 6.0 * sumSquaredGaps / (n * (n.toDouble() * n - 1))

    println("rainfall-to-wetness rank correlation over $n land regions: %.3f".format(rho))
    assertTrue(rho > 0.85, "rainfall and wetness correlate at only %.3f".format(rho))

    // And the absolute end that a correlation cannot see: the driest ground has to be genuinely dry, or a
    // desert is merely the least wet place in a world where it rains everywhere.
    val driest = samples.minBy { it.mmPerDay }
    assertTrue(
      driest.wet < 0.06,
      "the driest region (${driest.biome.label}, %.1f mm/day) is still wet %.3f of the year"
        .format(driest.mmPerDay, driest.wet)
    )
  }

  @Test
  fun `a seasonal region gets its rain in the right half of the year, on the right side of the equator`() {
    val seasonality = world.world.layers.require<net.bestia.worldgen.core.FloatLayer>(
      LayerId.PRECIPITATION_SEASONALITY
    )

    var checked = 0

    for (region in model.regions.regions) {
      if (region.landShare < 0.5) continue
      if (seasonality.sampleBilinear(region.centre.x, region.centre.y) < 0.45) continue

      // And it has to actually rain here. `PRECIPITATION_SEASONALITY` is a *relative* spread, so it happily
      // flags a polar region getting 27 to 39 mm a quarter as strongly seasonal - and in a place where rain is
      // a once-a-month event, which quarter a finite sample measures as wettest is noise with no signal under
      // it. That is how this test first failed: on a region whose whole annual rainfall is 133 mm.
      if (region.seasonalPrecipitation.average() / WeatherRegion.DAYS_PER_QUARTER < MIN_MM_PER_DAY) continue

      // Wet fraction per quarter, **averaged over several years**. One year is not a climatology: the field
      // is strongly autocorrelated at a 1.2-day period, so a thirty-day quarter holds about twenty-five
      // independent samples and the wettest *measured* quarter of a single year can differ from the
      // climatologically wettest one wherever two quarters are close. Measured on one year this asserted a
      // real property against too little data and failed on a southern region whose two wettest quarters are
      // nearly equal.
      val perQuarter = DoubleArray(4)
      for (quarter in 0 until 4) {
        val states = ArrayList<WeatherState>()
        for (yearIndex in 0 until YEARS) {
          var day = yearIndex * WeatherModel.DAYS_PER_YEAR + quarter * 30.0
          val end = yearIndex * WeatherModel.DAYS_PER_YEAR + (quarter + 1) * 30.0
          while (day < end) {
            val yearProgress = (day % WeatherModel.DAYS_PER_YEAR) / WeatherModel.DAYS_PER_YEAR
            val air = temperature.at(
              region.centre.x, region.centre.y, region.meanElevation, yearProgress, day - Math.floor(day)
            ).airCelsius
            states.add(model.at(region, day, air))
            day += 0.25
          }
        }
        perQuarter[quarter] = wetFraction(states)
      }

      val wettest = perQuarter.indices.maxBy { perQuarter[it] }
      val rainfall = region.seasonalPrecipitation
      val meanRainfall = rainfall.average()

      // **The hemisphere trap, asserted rather than assumed.** The quarter the model actually rains in most
      // has to be one that is wetter than this region's average - which a sign error in `Seasons.warmingAt`
      // or a quarter-index off-by-one would break in the same direction everywhere, putting every monsoon in
      // the wrong half of the year with nothing else in the module noticing.
      //
      // Deliberately *not* "the wettest quarter exactly". Two quarters within a few percent of each other are
      // common - the equinox pair especially - and which of them a finite sample measures as wetter is noise,
      // not a property. The first version demanded the exact index and failed on a region whose top two
      // quarters differed by 6%.
      assertTrue(
        rainfall[wettest] > meanRainfall,
        "region ${region.index} rains most in quarter $wettest, which is drier than its own average " +
            "(${rainfall.joinToString(",") { "%.0f".format(it) }}, latitude ${"%.1f".format(region.latitude)})"
      )
      checked++
    }

    println("seasonality check covered $checked region(s) with a pronounced wet season")
  }

  /** Years averaged when measuring a climatology. See the seasonality test for why one is not enough. */
  private val YEARS = 8

  /** Rain a region must get before its wet season is measurable rather than noise, in mm per Bestia day. */
  private val MIN_MM_PER_DAY = 6.0

  /** Biomes whose rainfall calibration is printed, for tuning `rainRateMmPerDay`. */
  private val DIAGNOSED = setOf(
    Biome.DESERT, Biome.COLD_DESERT, Biome.TROPICAL_RAINFOREST, Biome.TEMPERATE_RAINFOREST,
    Biome.GRASSLAND, Biome.TEMPERATE_FOREST
  )

  @Test
  fun `a peak and a valley in one region disagree about rain and snow`() {
    // The property that makes a 16 km region acceptable: the region owns the channels, the *position* owns the
    // interpretation. Without it a mountain range shares its valley's weather and snow never falls on it.
    val region = model.regions.regions
      .filter { it.landShare > 0.5 && it.relief > 400.0 }
      .maxByOrNull { it.relief }
      ?: return

    var disagreements = 0
    var day = 0.0
    while (day < WeatherModel.DAYS_PER_YEAR) {
      val yearProgress = day / WeatherModel.DAYS_PER_YEAR
      val hour = day - Math.floor(day)

      val low = temperature.at(
        region.centre.x, region.centre.y, region.meanElevation - region.relief / 2, yearProgress, hour
      ).airCelsius
      val high = temperature.at(
        region.centre.x, region.centre.y, region.meanElevation + region.relief / 2, yearProgress, hour
      ).airCelsius

      val below = model.at(region, day, low)
      val above = model.at(region, day, high)
      if (below.kind != above.kind) disagreements++

      day += 0.25
    }

    assertTrue(
      disagreements > 0,
      "the peak and the valley of a ${region.relief.toInt()} m region never differ; " +
          "the temperature split is not reaching the classification"
    )
  }
}
