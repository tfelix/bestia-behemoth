package net.bestia.worldgen.climate

import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.LayerStore
import net.bestia.worldgen.core.WorldConfig
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sqrt

/** Air temperature and what it feels like, at a place and a moment. */
data class Temperature(
  /** Air temperature in degrees Celsius. */
  val airCelsius: Double,

  /** What it feels like: wind chill below 10 degrees, humidity above 25. Gameplay keys on this. */
  val feelsLikeCelsius: Double
)

/**
 * Temperature at a position, on a day, at an hour, in this weather.
 *
 * `REMINDER.md` asks for exactly this, and for the property that makes it worth having: comfortable all year in
 * the low-level country, and swinging hard in the deserts, the mountains and the high-mana ground, so that the
 * hostile places need equipment rather than merely holding harder monsters.
 *
 * A stateless reader over stored layers, sampled in world metres - the [SeasonalPrecipitation] shape. The
 * runtime supplies the time; nothing here has a clock.
 *
 * ### Five terms
 *
 * 1. `TEMPERATURE`, the mean annual.
 * 2. The **elevation residual**, which is the term nobody expects to need. `TEMPERATURE` already has the lapse
 *    rate applied at the *climate cell's* mean elevation - `ClimateStage` resamples `BEDROCK_ELEVATION` onto a
 *    four-kilometre grid to build it - so a column has to be corrected for how far it sits from that cell's
 *    mean, not from sea level. Skip it and a 400 m spur inside a cell averaging 100 m reads about 1.9 °C too
 *    warm in ordinary terrain and up to 6 °C in an orogen.
 * 3. The **seasonal** swing, through [Seasons.warmingAt] so the runtime and the generator cannot drift.
 * 4. The **diurnal** swing, which is where aridity, continentality and cloud come in.
 * 5. The **weather** modifier, an exhaustive `when` over [WeatherKind] with no `else`.
 *
 * ### `SurfaceSampler.temperatureAt` is not this and must not become it
 *
 * That one feeds `SurfaceCover`, whose snow line is documented as the *mean annual* threshold for
 * **permanent** snow. A block in a cached chunk must never depend on the time of year, or the chunk cache key
 * needs a timestamp in it. Seasonal snow is a client visual driven by the weather message.
 */
class LocalTemperature private constructor(
  private val temperature: FloatLayer,
  private val range: FloatLayer,
  private val toOcean: FloatLayer,
  private val precipitation: FloatLayer,
  private val bedrock: FloatLayer,
  private val config: WorldConfig,
  private val climate: ClimateParams,
  private val weather: WeatherParams
) {

  /**
   * @param columnElevation the actual ground height at the position, in metres
   * @param yearProgress fraction of the year elapsed
   * @param timeOfDay fraction of the day elapsed, `0` at midnight
   * @param state the weather here now, or null for the air temperature without weather
   * @param sheltered whether the column is under a roof; see [WeatherParams.shelterDamping]
   */
  fun at(
    worldX: Double,
    worldY: Double,
    columnElevation: Double,
    yearProgress: Double,
    timeOfDay: Double,
    state: WeatherState? = null,
    sheltered: Boolean = false
  ): Temperature {
    val mean = temperature.sampleBilinear(worldX, worldY)
    val swing = range.sampleBilinear(worldX, worldY)

    val residual = -climate.lapseRate * (columnElevation - climateCellElevation(worldX, worldY)) / 1000.0

    val northwards = worldY / config.heightMetres
    val seasonal = swing * Seasons.warmingAt(yearProgress, northwards)

    val cloud = state?.cloudCover ?: 0.0
    val diurnalKept = if (sheltered) weather.shelterDamping else 1.0
    val diurnal = diurnalAmplitude(worldX, worldY, cloud) *
        cos(TAU * (timeOfDay - HOTTEST_TIME_OF_DAY)) * diurnalKept

    val weatherDelta = if (state == null) 0.0 else {
      val kept = if (sheltered && !state.kind.ignoresShelter) weather.shelterDamping else 1.0
      deltaOf(state) * kept
    }

    val air = mean + residual + seasonal + diurnal + weatherDelta
    return Temperature(air, feelsLike(air, state))
  }

  /**
   * The climate cell's own mean elevation at this position.
   *
   * A box average over the base-grid samples inside the cell, which is what `Grid.resampled` produced when
   * `ClimateStage` built the temperature field - so this reverses that resample rather than approximating it.
   * The cell size is read off the layer instead of assumed: climate runs four times coarser than the
   * heightfield, except on worlds too small for that.
   */
  private fun climateCellElevation(worldX: Double, worldY: Double): Double {
    val cell = temperature.region.resolution.metresPerCell
    val originX = Math.floor(worldX / cell) * cell
    val originY = Math.floor(worldY / cell) * cell
    val step = cell / TAPS_PER_AXIS

    var sum = 0.0
    for (row in 0 until TAPS_PER_AXIS) {
      for (column in 0 until TAPS_PER_AXIS) {
        sum += bedrock.sampleBilinear(
          originX + (column + 0.5) * step,
          originY + (row + 0.5) * step
        )
      }
    }
    return sum / (TAPS_PER_AXIS * TAPS_PER_AXIS)
  }

  /**
   * How far the day swings from its mean, in degrees.
   *
   * Grows with continentality and aridity and shrinks under cloud. A continental desert comes out around
   * 16 °C against a coast's 4 °C, where Earth's Sahara is about 20 and a maritime coast about 6.
   *
   * `maritimeRange` goes through `scaleByLength` because `ClimateStage` does. Recomputing continentality
   * without it would disagree with the `TEMPERATURE_RANGE` this sits beside - the `WorldParams.resolved` class
   * of bug in a new place.
   */
  private fun diurnalAmplitude(worldX: Double, worldY: Double, cloud: Double): Double {
    val maritimeRange = config.scaleByLength(climate.maritimeRange)
    val continentality = 1.0 - exp(-toOcean.sampleBilinear(worldX, worldY) / maritimeRange)

    val annualRain = precipitation.sampleBilinear(worldX, worldY)
    val aridity = (1.0 - annualRain / ARID_REFERENCE_MM).coerceIn(0.0, 1.0)

    return (weather.diurnalBase + weather.diurnalContinentality * continentality) *
        (ARIDITY_FLOOR + (1.0 - ARIDITY_FLOOR) * aridity) *
        (1.0 - weather.cloudDamping * cloud)
  }

  /**
   * What the weather does to the air temperature.
   *
   * Exhaustive over [WeatherKind] with **no `else`**, so a new kind is a compile error until somebody says how
   * cold it is. `CLEAR` and `CLOUDY` are zero on purpose: cloud already acts through the diurnal term, and a
   * second term for it would double-count.
   */
  private fun deltaOf(state: WeatherState): Double {
    val i = state.intensity
    return when (state.kind) {
      WeatherKind.CLEAR, WeatherKind.CLOUDY -> 0.0
      WeatherKind.FOG -> -1.0
      WeatherKind.RAIN -> -2.5 * i
      WeatherKind.HEAVY_RAIN -> -4.0 * i
      WeatherKind.THUNDERSTORM, WeatherKind.TORNADO -> -5.0 * i
      WeatherKind.SNOW -> -2.0 * i
      WeatherKind.BLIZZARD -> -6.0 * i
      // Suspended dust, and it is a desert underneath. The one kind that warms.
      WeatherKind.SANDSTORM -> 3.0 * i
      // Either sign, from the state's own anomaly - see WeatherState.manaAnomaly.
      WeatherKind.MANA_STORM -> 8.0 * i * state.manaAnomaly
    }
  }

  private fun feelsLike(air: Double, state: WeatherState?): Double {
    val wind = state?.windSpeed ?: 0.0
    return when {
      air < CHILL_BELOW -> air - CHILL_PER_MS * sqrt(wind)
      air > HUMID_ABOVE && state?.kind?.precipitating == true -> air + HUMID_BONUS
      else -> air
    }
  }

  companion object {
    private const val TAU = 2.0 * Math.PI

    /**
     * Fraction of the day at which it is hottest.
     *
     * **Not 0.5.** A Bestia day is eighteen hours of light and six of dark, with night at hours `[0,6)`, so
     * solar noon is at 0.625 rather than at midday - and peak temperature lags solar noon. Getting this wrong
     * puts the hottest hour at dawn.
     */
    const val HOTTEST_TIME_OF_DAY = 0.71

    /** Taps per axis when reversing the climate resample. Sixteen samples, about fifty nanoseconds. */
    private const val TAPS_PER_AXIS = 4

    /** Annual rainfall at which a place is not arid at all, in millimetres. */
    private const val ARID_REFERENCE_MM = 1_400.0

    /** Share of the diurnal swing even a soaking climate keeps. */
    private const val ARIDITY_FLOOR = 0.55

    private const val CHILL_BELOW = 10.0
    private const val CHILL_PER_MS = 0.7
    private const val HUMID_ABOVE = 25.0
    private const val HUMID_BONUS = 2.0

    /**
     * Reads the layers, or null on a world with no climate stage in it.
     *
     * Nullable rather than throwing for [SeasonalPrecipitation.from]'s reason: the stage tests run a handful of
     * stages and the viewer opens on partial pipelines.
     */
    fun from(
      layers: LayerStore,
      config: WorldConfig,
      climate: ClimateParams = ClimateParams(),
      weather: WeatherParams = WeatherParams()
    ): LocalTemperature? {
      val temperature = layers[LayerId.TEMPERATURE] as? FloatLayer ?: return null
      val range = layers[LayerId.TEMPERATURE_RANGE] as? FloatLayer ?: return null
      val toOcean = layers[LayerId.DISTANCE_TO_OCEAN] as? FloatLayer ?: return null
      val precipitation = layers[LayerId.PRECIPITATION] as? FloatLayer ?: return null
      val bedrock = layers[LayerId.BEDROCK_ELEVATION] as? FloatLayer ?: return null

      return LocalTemperature(
        temperature, range, toOcean, precipitation, bedrock, config, climate, weather
      )
    }
  }
}
