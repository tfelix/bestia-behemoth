package net.bestia.worldgen.climate

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.LayerStore
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.WorldConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The shape of the geothermal term, on synthetic layers rather than on a generated world.
 *
 * Deliberately not in `LocalTemperatureTest`, which measures what a *world* reaches and needs a world to do it.
 * The questions here are about the curve - how much lift a given volcanism buys, and when in the day - and every
 * one of them is answered by subtracting two samplers that differ in exactly one layer. That makes the lift
 * exact: no term has to be modelled or held constant by hand, because both sides carry all five of the others.
 *
 * What each assertion is protecting:
 *
 * - **the cutoff.** `geothermalFloor` is the difference between a term that marks out three provinces and a
 *   global offset on the air temperature. `LayerId.VOLCANISM` is a percentile rank, so a linear ramp from zero
 *   would warm *forty-five per cent of the land* by something, which is the failure `WeatherParams.manaThreshold`
 *   documents in the same file for the same reason.
 * - **the smoothstep.** The number that keeps a high-latitude basin a warm spot instead of a jungle.
 * - **it does not follow the sun.** The observable that reads as "the ground here is warm" rather than "this
 *   region is hot", and the one that would break `a continental desert swings harder than a coast` if the term
 *   were scaled by the diurnal cosine instead of merely being more noticeable at night.
 * - **it is optional.** A world generated without `VolcanismStage` - which every partial pipeline in the stage
 *   tests is, and which the viewer opens on - must still have a local temperature.
 */
class GeothermalTest {

  @Test
  fun `a world with no volcanism layer still has a temperature, and no lift`() {
    val plain = temperatureOf(volcanism = null)
    assertNotNull(plain, "a world without VolcanismStage must still have a local temperature")

    // Same as an all-zero layer, at every hour. A null layer taken as "hot" or as NaN is the failure this pins,
    // and NaN in particular would propagate silently into the air temperature of the whole world.
    val zeroed = temperatureOf(volcanism = 0.0)!!
    for (hour in 0 until 8) {
      assertEquals(
        zeroed.at(AT_X, AT_Y, 0.0, 0.4, hour / 8.0).airCelsius,
        plain.at(AT_X, AT_Y, 0.0, 0.4, hour / 8.0).airCelsius,
        1e-9,
        "a missing volcanism layer must behave exactly as an unvolcanic one"
      )
    }
  }

  @Test
  fun `ordinary country gets nothing at all`() {
    // At and below the floor the lift is exactly zero, not merely small. Forty-five per cent of the land sits
    // below it by construction, and that land is what "comfortable all year in the low-level country" is about.
    val floor = WeatherParams().geothermalFloor

    assertEquals(0.0, liftAt(0.0), 0.0)
    assertEquals(0.0, liftAt(floor * 0.5), 0.0)
    // A whisker of tolerance at the floor exactly, and only there: a `FloatLayer` stores 0.55 as 0.550000011,
    // so the cell is a hair *above* the floor and the smoothstep answers 3e-14 rather than 0. Which is the
    // truthful answer to what was asked - and 3e-14 C is not a warm cell.
    assertEquals(0.0, liftAt(floor), 1e-9, "the floor itself must be the last cold cell, not the first warm one")
  }

  @Test
  fun `a high-latitude basin is warmed and not made tropical`() {
    // Volcanism 0.85 is a basin on the flank of a field rather than a crater. The smoothstep gives it
    // 14 x smoothstep(0.4) = 4.9 C, which turns a tundra basin at -8 C into one at -3 - a warm spot, which is
    // what a geothermal basin is. A linear ramp would hand the same cell 5.6 C, and the peak 14.
    val lift = liftAt(BASIN)

    println("geothermal lift: 0.80 -> %.2f C, %.2f -> %.2f C, 0.95 -> %.2f C, 1.00 -> %.2f C".format(
      liftAt(0.80), BASIN, lift, liftAt(0.95), liftAt(1.0)
    ))

    assertTrue(lift > 2.0, "volcanism $BASIN lifted only ${"%.2f".format(lift)} C, which is not a warm spot")
    assertTrue(lift < 6.0, "volcanism $BASIN lifted ${"%.2f".format(lift)} C, which is a climate change")

    // And a linear ramp is what it must not be. Measured as a ratio rather than asserted tightly, because the
    // claim is "sub-linear over the lower half of the range" and not "exactly this Hermite polynomial".
    val linear = WeatherParams().let {
      it.geothermalPeak * (BASIN - it.geothermalFloor) / (1.0 - it.geothermalFloor)
    }
    assertTrue(lift < linear, "the ramp is linear: ${"%.2f".format(lift)} against ${"%.2f".format(linear)}")
  }

  @Test
  fun `the core of a field gets the whole peak`() {
    // At the top of the rank the daytime lift is the peak itself, so `geothermalPeak` means what its KDoc says
    // and the number can be reasoned about against the 33-34 C the world already reaches.
    val peak = WeatherParams().geothermalPeak
    val lift = liftAt(1.0, timeOfDay = LocalTemperature.HOTTEST_TIME_OF_DAY)

    assertEquals(peak, lift, 0.01, "the hottest hour at the top of the rank should be the peak exactly")
  }

  @Test
  fun `the geothermal term does not follow the sun`() {
    val hottest = liftAt(1.0, timeOfDay = LocalTemperature.HOTTEST_TIME_OF_DAY)
    val coldest = liftAt(1.0, timeOfDay = (LocalTemperature.HOTTEST_TIME_OF_DAY + 0.5) % 1.0)

    // Larger at night, not smaller. A term scaled by the diurnal cosine would be *zero* at the coldest hour and
    // would read as "this region is hot" rather than as "the ground here is warm".
    assertTrue(
      coldest > hottest,
      "ground heat should be more noticeable at night: ${"%.2f".format(coldest)} C against " +
          "${"%.2f".format(hottest)} C by day"
    )

    // And the daily *swing* does not grow, which is what protects `a continental desert swings harder than a
    // coast`: that test compares swings, and a solar-scaled geothermal term would inflate the volcanic one.
    val volcanic = swingAt(1.0)
    val ordinary = swingAt(0.0)
    println("daily swing: volcanic %.2f C, unvolcanic %.2f C".format(volcanic, ordinary))
    assertTrue(
      volcanic <= ordinary + 1e-9,
      "a volcanic cell swings ${"%.2f".format(volcanic)} C against an unvolcanic ${"%.2f".format(ordinary)} C"
    )
  }

  /** Degrees the ground adds at one volcanism, measured against the identical world without the layer. */
  private fun liftAt(volcanism: Double, timeOfDay: Double = LocalTemperature.HOTTEST_TIME_OF_DAY): Double {
    val warm = temperatureOf(volcanism)!!.at(AT_X, AT_Y, 0.0, YEAR_PROGRESS, timeOfDay).airCelsius
    val cold = temperatureOf(null)!!.at(AT_X, AT_Y, 0.0, YEAR_PROGRESS, timeOfDay).airCelsius
    return warm - cold
  }

  /** Difference between the hottest and coldest air temperature over one day at one volcanism. */
  private fun swingAt(volcanism: Double): Double {
    val at = temperatureOf(volcanism)!!
    val day = (0 until 48).map { at.at(AT_X, AT_Y, 0.0, YEAR_PROGRESS, it / 48.0).airCelsius }
    return day.max() - day.min()
  }

  /**
   * A featureless world at one uniform volcanism, or with no volcanism layer at all when [volcanism] is null.
   *
   * Every layer is constant, so the five other terms are the same on both sides of every subtraction above and
   * cancel exactly. No seasonal range, so the year does not enter either.
   */
  private fun temperatureOf(volcanism: Double?): LocalTemperature? {
    val cells = region.cellCount.toInt()
    val layers = LayerStore()
    val producer = StageId("fixture")

    layers.put(producer, FloatLayer(LayerId.TEMPERATURE, region, FloatArray(cells) { MEAN_ANNUAL }))
    layers.put(producer, FloatLayer(LayerId.TEMPERATURE_RANGE, region, FloatArray(cells)))
    layers.put(producer, FloatLayer(LayerId.DISTANCE_TO_OCEAN, region, FloatArray(cells)))
    layers.put(producer, FloatLayer(LayerId.PRECIPITATION, region, FloatArray(cells) { WET }))
    layers.put(producer, FloatLayer(LayerId.BEDROCK_ELEVATION, region, FloatArray(cells)))
    if (volcanism != null) {
      layers.put(producer, FloatLayer(LayerId.VOLCANISM, region, FloatArray(cells) { volcanism.toFloat() }))
    }

    return LocalTemperature.from(layers, config)
  }

  private companion object {
    const val MEAN_ANNUAL = 10f

    /** Past `ARID_REFERENCE_MM`, so aridity is zero and the diurnal amplitude is at its floor. */
    const val WET = 1_600f

    const val YEAR_PROGRESS = 0.4

    /**
     * A cell on the flank of a volcanic field: warm ground, not a crater.
     *
     * Above `geothermalFloor` by four tenths of the range that is left, so it exercises the middle of the
     * smoothstep rather than either end. Written as a constant so that moving the floor moves what this test
     * measures with it - a hard-coded 0.85 would silently become "below the floor" and assert nothing.
     */
    val BASIN = WeatherParams().let { it.geothermalFloor + 0.4 * (1.0 - it.geothermalFloor) }

    /** Somewhere in the middle of the fixture, away from the bilinear edge. */
    const val AT_X = 4_000.0
    const val AT_Y = 4_000.0

    val config = WorldConfig(seed = 11L, widthCells = 8, heightCells = 8)
    val region = CellRegion.world(8, 8, Resolution.KILOMETRE)
  }
}
