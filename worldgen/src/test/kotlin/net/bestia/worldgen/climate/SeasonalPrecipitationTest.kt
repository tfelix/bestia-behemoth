package net.bestia.worldgen.climate

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.pipeline.StandardWorld
import java.util.Locale
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The four seasonal precipitation layers and the monthly curve derived from them.
 *
 * The layers' whole claim is that they are a *decomposition* of `PRECIPITATION` rather than a second opinion
 * about it, so the load-bearing test here is that they sum to it. That claim was false in the obvious first
 * implementation: `scaleToMean` calibrated the annual field to a target and computed its factor locally, which
 * cost nothing while the seasonal grids were discarded a line later and makes the four layers wrong the moment
 * they are stored.
 *
 * The rest is about the seasons being genuinely four rather than two pairs. A sinusoidal belt shift on its own
 * gives the two equinoxes the same belt position, and if the temperature cycle shares its phase they are the
 * same field twice - so `spring != autumn` is not a nicety, it is what says the layer count is honest.
 */
class SeasonalPrecipitationTest {

  private companion object {
    /** One world, built once. Twelve stages at 192 cells is a few seconds and every test here reads it. */
    val world: GeneratedWorld = StandardWorld.build(
      StandardWorld.demoConfig(seed = StandardWorld.DEFAULT_SEED).copy(widthCells = 192, heightCells = 192)
    )

    fun layer(id: LayerId): FloatLayer = world.world.layers.require(id)

    val seasonal = SeasonalPrecipitation.from(world.world.layers)!!
  }

  // --- The decomposition ------------------------------------------------------------------------------

  @Test
  fun `the four seasons sum to the annual field`() {
    val annual = layer(LayerId.PRECIPITATION)
    val seasons = SeasonalPrecipitation.LAYERS.map { layer(it) }

    var worst = 0.0
    var worstAt = -1
    for (i in annual.data.indices) {
      val sum = seasons.sumOf { it.data[i].toDouble() }
      val delta = abs(sum - annual.data[i])
      if (delta > worst) {
        worst = delta
        worstAt = i
      }
    }

    // Float layers, and the annual field is a sum of four values each around 500 mm, so the tolerance is
    // float epsilon at that magnitude rather than a fudge factor. A scaling bug shows up as hundreds of mm.
    assertTrue(
      worst < 0.5,
      "the seasonal layers do not sum to PRECIPITATION: worst cell $worstAt is off by " +
          "${"%.4f".format(Locale.ROOT, worst)} mm"
    )
  }

  @Test
  fun `no season is negative anywhere`() {
    for (id in SeasonalPrecipitation.LAYERS) {
      val min = layer(id).data.min()
      assertTrue(min >= -1e-4, "$id goes negative: $min")
    }
  }

  @Test
  fun `the annual field is still calibrated to the configured mean`() {
    // The point of routing the scale factor through the seasonal fields is that it must not change what
    // PRECIPITATION itself says - seven consumers read it and none of them were meant to move.
    val annual = layer(LayerId.PRECIPITATION)
    val mean = annual.data.sumOf { it.toDouble() } / annual.data.size

    assertEquals(
      ClimateParams().meanPrecipitation, mean, 1.0,
      "the annual mean drifted away from meanPrecipitation"
    )
  }

  // --- Four seasons, not two -------------------------------------------------------------------------

  @Test
  fun `spring and autumn are different fields`() {
    val spring = layer(LayerId.PRECIPITATION_SPRING)
    val autumn = layer(LayerId.PRECIPITATION_AUTUMN)

    var differing = 0
    for (i in spring.data.indices) {
      if (abs(spring.data[i] - autumn.data[i]) > 1.0) differing++
    }

    // The two equinoxes share a temperature and differ only through the monsoon lag's belt offset, so this is
    // a smaller difference than summer-to-winter by design. What it must not be is zero: that would mean two
    // identical layers on disk, and the four-layer decision would be a two-layer one paying twice.
    val share = differing.toDouble() / spring.data.size
    assertTrue(
      share > 0.5,
      "spring and autumn are the same field over ${"%.1f".format(Locale.ROOT, 100 * (1 - share))}% of the " +
          "world - the belt shift and the thermal cycle are probably in phase"
    )
  }

  @Test
  fun `summer and winter differ more than the equinoxes do`() {
    val spring = layer(LayerId.PRECIPITATION_SPRING)
    val summer = layer(LayerId.PRECIPITATION_SUMMER)
    val autumn = layer(LayerId.PRECIPITATION_AUTUMN)
    val winter = layer(LayerId.PRECIPITATION_WINTER)

    var solstices = 0.0
    var equinoxes = 0.0
    for (i in spring.data.indices) {
      solstices += abs(summer.data[i] - winter.data[i])
      equinoxes += abs(spring.data[i] - autumn.data[i])
    }

    // The ordering is the physics: the solstices are the thermal extremes and the equinoxes are separated
    // only by the lag. If this inverts, the temperature term has stopped reaching the sweep.
    assertTrue(
      solstices > equinoxes,
      "the equinoxes differ more than the solstices ($equinoxes vs $solstices) - the seasonal temperature " +
          "is probably not reaching Winds.capacity"
    )
  }

  @Test
  fun `the hemispheres have their wet seasons in opposite halves of the year`() {
    // The property four fields exist for. At matched distance from the equator the same orbital phase warms
    // one hemisphere and cools the other, so a northern cell's wettest season should not generally be a
    // southern cell's - and with a shared phase it would be, everywhere.
    val bounds = world.world.config.worldBounds
    val midX = bounds.minX + bounds.width / 2.0
    val centreY = bounds.minY + bounds.height / 2.0
    val quarter = bounds.height / 4.0

    var opposed = 0
    var compared = 0
    for (step in 1 until 32) {
      val x = bounds.minX + bounds.width * step / 32.0
      val north = seasonal.wettestSeason(x, centreY + quarter)
      val south = seasonal.wettestSeason(x, centreY - quarter)
      compared++

      // Two of four seasons apart is half a year - the summer/winter flip. Spring/autumn pairs are the lag's
      // doing and count as neither confirming nor denying.
      if (abs(north - south) == 2) opposed++
    }

    assertTrue(midX > 0.0)
    assertTrue(
      opposed > compared / 4,
      "only $opposed of $compared matched latitudes had opposed wet seasons; the hemispheres look in phase"
    )
  }

  // --- The monthly curve -----------------------------------------------------------------------------

  @Test
  fun `a month at a season centre returns that season`() {
    val bounds = world.world.config.worldBounds
    val x = bounds.minX + bounds.width * 0.42
    val y = bounds.minY + bounds.height * 0.61

    // Months 1.5, 4.5, 7.5, 10.5 are the quarter centres, and the spline interpolates its control points,
    // so each must come back exactly - divided by the months in a quarter, since atMonth returns a month.
    for (season in 0 until SeasonalPrecipitation.COUNT) {
      val monthsPerSeason = ClimateStage.MONTHS_PER_YEAR / SeasonalPrecipitation.COUNT
      val centre = season * monthsPerSeason + monthsPerSeason / 2.0

      assertEquals(
        seasonal.atSeason(season, x, y) / monthsPerSeason,
        seasonal.atMonth(x, y, centre),
        1e-9,
        "month $centre should be season $season exactly"
      )
    }
  }

  @Test
  fun `the monthly curve is periodic`() {
    val bounds = world.world.config.worldBounds
    val x = bounds.minX + bounds.width * 0.3
    val y = bounds.minY + bounds.height * 0.7

    // Wrapping is what a clamped spline would get wrong, and it would get it wrong precisely at the new year,
    // where a corner is easy to miss and would show up in a food model as a January nobody can farm.
    for (month in 0 until 12) {
      assertEquals(
        seasonal.atMonth(x, y, month.toDouble()),
        seasonal.atMonth(x, y, month + 12.0),
        1e-9,
        "month $month and month ${month + 12} disagree"
      )
      assertEquals(
        seasonal.atMonth(x, y, month.toDouble()),
        seasonal.atMonth(x, y, month - 12.0),
        1e-9,
        "month $month and month ${month - 12} disagree"
      )
    }
  }

  @Test
  fun `the monthly curve is never negative and roughly conserves the annual total`() {
    val annual = layer(LayerId.PRECIPITATION)
    val bounds = world.world.config.worldBounds

    var worstError = 0.0
    for (step in 0 until 64) {
      val x = bounds.minX + bounds.width * (step + 0.5) / 64.0
      val y = bounds.minY + bounds.height * ((step * 7) % 64 + 0.5) / 64.0

      var total = 0.0
      for (month in 0 until 12) {
        val value = seasonal.atMonth(x, y, month + 0.5)
        assertTrue(value >= 0.0, "month $month at ($x,$y) is negative: $value")
        total += value
      }

      val expected = annual.sampleBilinear(x, y)
      if (expected > 1.0) worstError = maxOf(worstError, abs(total - expected) / expected)
    }

    // Catmull-Rom is interpolating rather than mass-conserving, so summing twelve monthly samples is close to
    // the annual total but not equal to it. The tolerance is measured rather than assumed - see the
    // measurement in the failure message if it ever moves.
    assertTrue(
      worstError < 0.05,
      "summing the monthly curve drifts from the annual field by " +
          "${"%.2f".format(Locale.ROOT, 100 * worstError)}%"
    )
  }

  // --- The seasonality scalar ------------------------------------------------------------------------

  @Test
  fun `the seasonality index stays in range and responds to concentration`() {
    val index = layer(LayerId.PRECIPITATION_SEASONALITY)
    assertTrue(index.data.min() >= -1e-4, "seasonality goes negative: ${index.data.min()}")
    assertTrue(index.data.max() <= 1.0 + 1e-4, "seasonality exceeds 1: ${index.data.max()}")

    // It must not be flat: a constant index is what a broken concentration measure looks like, and BiomeStage
    // thresholds on it, so flat means the monsoon biomes are unreachable.
    assertTrue(
      index.data.max() - index.data.min() > 0.05,
      "the seasonality index is nearly constant across the world"
    )
  }

  @Test
  fun `the seasonality index is the spread between the solstices`() {
    // Why min-max survived the move to four seasons rather than being generalised - see the KDoc on
    // ClimateStage.seasonality, which records the biome reclassification a concentration index caused.
    //
    // The annual cycle is one sine, so the wettest and driest seasons should be the two solstices and the
    // equinoxes should lie between them. That is what makes min-max over four the same quantity as over two,
    // and it is the assumption the decision rests on - so it is asserted rather than trusted.
    val spring = layer(LayerId.PRECIPITATION_SPRING)
    val summer = layer(LayerId.PRECIPITATION_SUMMER)
    val autumn = layer(LayerId.PRECIPITATION_AUTUMN)
    val winter = layer(LayerId.PRECIPITATION_WINTER)

    var equinoxIsExtreme = 0
    for (i in spring.data.indices) {
      val solsticeLow = minOf(summer.data[i], winter.data[i])
      val solsticeHigh = maxOf(summer.data[i], winter.data[i])
      val equinoxLow = minOf(spring.data[i], autumn.data[i])
      val equinoxHigh = maxOf(spring.data[i], autumn.data[i])

      // A millimetre of slack: the mixing blur is a stencil and the two equinoxes are not exactly equal.
      if (equinoxLow < solsticeLow - 1.0 || equinoxHigh > solsticeHigh + 1.0) equinoxIsExtreme++
    }

    val share = equinoxIsExtreme.toDouble() / spring.data.size
    assertTrue(
      share < 0.25,
      "an equinox is the year's wettest or driest season on " +
          "${"%.1f".format(Locale.ROOT, 100 * share)}% of the world - the annual cycle is no longer " +
          "unimodal, so min-max seasonality is losing information and wants revisiting"
    )
  }
}
