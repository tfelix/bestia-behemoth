package net.bestia.worldgen.climate

import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.fields.Tables
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.cos
import kotlin.math.pow

/** The weather in one region at one moment. */
data class WeatherState(
  val kind: WeatherKind,

  /** How hard, `0` to `1`. Zero for [WeatherKind.CLEAR]. */
  val intensity: Double,

  /** Cloud cover, `0` to `1`. Separate from [intensity] because a dry sky can still be grey. */
  val cloudCover: Double,

  /** Metres per second. */
  val windSpeed: Double,

  /** Radians, `0` pointing east, counter-clockwise. Where the weather is coming *from* is the opposite. */
  val windDirection: Double,

  /**
   * How dry the ground here is, `0` (saturated) to `1` (tinder).
   *
   * ### What this is not
   *
   * **Not soil moisture, and not days-since-rain.** There is no state in this model and there must not be -
   * that is the whole reason a forecast is the same evaluation at a later `t`. This is the slow **air-mass**
   * channel standing in for the ground, and it is honest only at the multi-day scale that channel moves on.
   *
   * ### Why it has to come from the air mass
   *
   * A consumer could compute "is it raining right now" from [intensity] for itself, and that is exactly the
   * version that does not work: rain stops and the ground is instantly tinder again, so a fire lit thirty
   * seconds after a downpour spreads as fast as one lit in a drought. The air mass is the only signal in this
   * model with a memory longer than a front - `airmassPeriodDays` is four times `synopticPeriodDays` - so it
   * is the only thing here that can say "this region has been dry for a while".
   *
   * The current front still gets a veto through [intensity], because ground under active rain is wet whatever
   * the fortnight was like.
   */
  val dryness: Double = 0.0,

  /**
   * Sign and share of a mana storm's temperature anomaly, `-1` to `+1`; zero for every other kind.
   *
   * The sign is drawn from the region and the day rather than being always cold, because "high mana means
   * more extreme swings" means both ways.
   */
  val manaAnomaly: Double = 0.0,

  /** Where a tornado is, when [kind] is [WeatherKind.TORNADO]; null otherwise. */
  val hazard: Hazard? = null
) {
  /** A point hazard inside a region: something a player can actually run from. */
  data class Hazard(val position: Vec2d, val radiusMetres: Double)
}

/**
 * The weather, as a pure function of `(worldSeed, region, dayOfWorld)`.
 *
 * ### An advected noise field, not a Markov chain
 *
 * Two fbm channels are sampled at the region's centre *minus* a drift proportional to time, so the pattern
 * moves downwind. That single choice buys all three properties a weather model needs:
 *
 * - **Temporal coherence, structurally.** Consecutive days sample nearby points of a C2 field, and cloud is a
 *   lower threshold on the *same* channel as rain, so the field must climb through cloudy to reach rain and
 *   through rain to reach storm. Clear to tornado to clear is not improbable, it is impossible.
 * - **Spatial coherence.** A front covers several adjacent regions and sweeps across the map over days, so the
 *   wind direction a player is shown genuinely predicts where their weather is coming from.
 * - **O(1) random access to any day.** `WEATHER_SENSE` already exists in `skills.yml` and promises
 *   "+5min/lv how far ahead upcoming weather changes can be sensed". A Markov chain can only forecast *in
 *   distribution*; this forecasts the actual answer, by evaluating at `t + lookahead`.
 *
 * A chain was the other candidate and is the textbook answer. It is a **recurrence**, which is at odds with a
 * module whose noise KDoc opens "no state, no permutation table to initialise, and no evaluation order": it
 * would need re-anchoring at each season boundary, up to 120 iterations per query, and then a cache - which is
 * exactly the mutable-state-with-an-evaluation-order that `GenRng.derive` exists to eliminate.
 *
 * Two drift speeds an order of magnitude apart mean the sum never repeats on any period a player sees, so no
 * third noise dimension is needed.
 *
 * ### Climatology decides, the field only says how far from normal
 *
 * A region precipitates when its wetness channel clears `1 - wetDayProbability`, and that probability comes
 * from the region's *own* seasonal rainfall. So a desert needs the top two percent of the field - rare and
 * short, which is what a desert cloudburst is - and a monsoon region gets half its days wet in the wet quarter
 * and three percent in the dry one, with *which calendar quarter that is* falling out of the four seasonal
 * layers and the hemisphere flip for free.
 */
class WeatherModel(
  val regions: WeatherRegions,
  private val config: WorldConfig,
  private val params: WeatherParams,
  seed: Long
) {

  private val wetSeed = GenRng.mix64(seed xor WET_SALT)
  private val airmassSeed = GenRng.mix64(seed xor AIRMASS_SALT)
  private val severitySeed = GenRng.mix64(seed xor SEVERITY_SALT)
  private val windSeed = GenRng.mix64(seed xor WIND_SALT)
  private val veerSeed = GenRng.mix64(seed xor VEER_SALT)

  private val synopticWavelength = config.scaleByLength(params.synopticWavelength)
  private val airmassWavelength = config.scaleByLength(params.airmassWavelength)

  /** The weather in the region covering a position, on a given day. */
  fun at(worldX: Double, worldY: Double, dayOfWorld: Double, temperatureCelsius: Double): WeatherState =
    at(regions.regionAt(worldX, worldY), dayOfWorld, temperatureCelsius)

  /**
   * The weather in a region on a given day.
   *
   * [temperatureCelsius] is passed in rather than computed here, and that is the split that lets a valley get
   * rain while the peak above it gets snow **in the same region at the same instant**: the region owns the
   * channels, the *position* owns the interpretation. It is also what keeps this class free of the layers.
   */
  fun at(region: WeatherRegion, dayOfWorld: Double, temperatureCelsius: Double): WeatherState {
    val yearProgress = Math.floorMod(dayOfWorld.toLong(), DAYS_PER_YEAR).toDouble() / DAYS_PER_YEAR
    val timeOfDay = dayOfWorld - Math.floor(dayOfWorld)

    // The synoptic channel alone, and it stays exactly uniform - which is the whole reason the thresholds in
    // `WeatherParams` mean their own face value.
    //
    // The first version averaged this with the air-mass channel *after* squashing both, and that was a
    // statistical error rather than a tuning one: a weighted sum of two independent uniforms is not uniform,
    // it is concentrated on its middle. The combined field almost never left `[0.3, 0.7]`, so a rainforest
    // asking for the top two thirds of it came out wet nine days in a hundred. The air mass now shifts the
    // *probability* instead, which is also the more physical statement - a wet fortnight makes rain likelier,
    // it does not make today's front wetter.
    val wetness = channel(wetSeed, region, dayOfWorld)
    val airmass = drifted(airmassSeed, region, dayOfWorld, airmassWavelength, params.airmassPeriodDays)
    val regimeShift = (airmass - 0.5) * 2.0 * (1.0 - params.synopticShare)

    val wetProbability = (wetDayProbability(region, yearProgress) * (1.0 + regimeShift))
      .coerceIn(0.0, params.maxWetFraction)
    val precipitating = wetProbability > 0.0 && wetness > 1.0 - wetProbability

    val intensity = if (!precipitating) 0.0 else {
      val above = (wetness - (1.0 - wetProbability)) / wetProbability
      above.coerceIn(0.0, 1.0).pow(params.rainSkew)
    }

    // Cloud is the same channel read lower down, which is what makes the ladder monotone: a sky cannot rain
    // without having been overcast first.
    val cloud = wetness.coerceIn(0.0, 1.0)

    val wind = windAt(region, dayOfWorld)
    val severity = severityAt(region, dayOfWorld, timeOfDay)
    val frozen = temperatureCelsius < FREEZING

    val kind = classify(region, precipitating, intensity, cloud, wind.speed, severity, frozen, timeOfDay)

    val anomaly = if (kind != WeatherKind.MANA_STORM) 0.0 else {
      // Either sign, drawn from the region and the whole day so it does not flicker within one storm.
      val roll = GenRng.hashUnit(severitySeed, region.index.toLong(), Math.floor(dayOfWorld).toLong())
      if (roll < 0.5) -1.0 + 2.0 * roll else (roll - 0.5) * 2.0
    }

    return WeatherState(
      kind = kind,
      intensity = if (kind == WeatherKind.CLEAR || kind == WeatherKind.CLOUDY) 0.0 else
        maxOf(intensity, severity * SEVERITY_AS_INTENSITY),
      cloudCover = cloud,
      windSpeed = wind.speed,
      windDirection = wind.direction,
      // The air mass carries the fortnight, `intensity` vetoes for the front standing over it right now. See
      // `WeatherState.dryness` for why neither half is enough on its own.
      dryness = ((1.0 - airmass) * (1.0 - intensity)).coerceIn(0.0, 1.0),
      manaAnomaly = anomaly,
      hazard = if (kind == WeatherKind.TORNADO) hazardAt(region, dayOfWorld) else null
    )
  }

  /**
   * The priority ladder.
   *
   * A subject-less `when`, so its trailing `CLEAR` is a real default rather than a swept-under `else` - judged
   * rather than swept. The order is the whole of the model's opinion about what outranks what.
   */
  private fun classify(
    region: WeatherRegion,
    precipitating: Boolean,
    intensity: Double,
    cloud: Double,
    wind: Double,
    severity: Double,
    frozen: Boolean,
    timeOfDay: Double
  ): WeatherKind = when {
    severity >= params.manaStormSeverity && manaFor(region) >= params.manaStormFloor -> WeatherKind.MANA_STORM

    severity >= params.tornadoSeverity && precipitating && !frozen &&
        convectivePotential(region) >= TORNADO_POTENTIAL -> WeatherKind.TORNADO

    severity >= params.thunderstormSeverity && precipitating && !frozen -> WeatherKind.THUNDERSTORM

    precipitating && frozen && intensity >= params.blizzardIntensity -> WeatherKind.BLIZZARD
    precipitating && frozen -> WeatherKind.SNOW

    precipitating && intensity >= params.heavyRainIntensity -> WeatherKind.HEAVY_RAIN
    precipitating -> WeatherKind.RAIN

    wind >= params.sandstormWind &&
        region.looseSurfaceShare >= params.sandstormSurfaceShare -> WeatherKind.SANDSTORM

    fogScoreOf(region, cloud, wind, timeOfDay) >= params.fogScore -> WeatherKind.FOG

    cloud >= params.cloudyCover -> WeatherKind.CLOUDY

    else -> WeatherKind.CLEAR
  }

  /** Share of days in this part of the year that see rain, from the region's own climatology. */
  fun wetDayProbability(region: WeatherRegion, yearProgress: Double): Double {
    val mmPerDay = region.rainfallAt(yearProgress)
    return (mmPerDay / (params.rainRateMmPerDay * params.meanWetIntensity))
      .coerceIn(0.0, params.maxWetFraction)
  }

  /**
   * How much extra storm a region's mana buys.
   *
   * Enters at exactly one place - the ceiling of the severity channel - so mana can make a place violent
   * without making it wet, which is what keeps a mana storm possible in a clear desert sky.
   */
  private fun manaBoost(region: WeatherRegion): Double {
    val excess = ((manaFor(region) - params.manaThreshold) / (1.0 - params.manaThreshold))
      .coerceAtLeast(0.0)
    return 1.0 + params.manaGain * excess.pow(params.manaExponent)
  }

  /**
   * The mana figure the thresholds read.
   *
   * The larger of the mean and a share of the peak, so a small hot spot inside an otherwise quiet region still
   * counts - a province is not required to be uniformly saturated to have storms in it.
   */
  private fun manaFor(region: WeatherRegion): Double =
    maxOf(region.meanMana, PEAK_MANA_WEIGHT * region.peakMana)

  /** Convective potential: warmth, relief and a wet air mass. What a thunderstorm is built out of. */
  private fun convectivePotential(region: WeatherRegion): Double {
    val warmth = ((region.meanTemperature - CONVECTION_COLD) / (CONVECTION_WARM - CONVECTION_COLD))
      .coerceIn(0.0, 1.0)
    val relief = (region.relief / CONVECTION_RELIEF).coerceIn(0.0, 1.0)
    return (warmth * 0.7 + relief * 0.3).coerceIn(0.0, 1.0)
  }

  private fun severityAt(region: WeatherRegion, dayOfWorld: Double, timeOfDay: Double): Double {
    val raw = drifted(severitySeed, region, dayOfWorld, synopticWavelength, params.synopticPeriodDays)
    // Convection peaks in the late afternoon. This is what gives the model sub-day structure, so a two-hour
    // session sees the sky change even when the synoptic channel has barely moved.
    val afternoon = 0.5 + 0.5 * cos(2.0 * Math.PI * (timeOfDay - AFTERNOON_PEAK))
    return (raw * afternoon * convectivePotential(region) * manaBoost(region)).coerceIn(0.0, 1.0)
  }

  private fun fogScoreOf(region: WeatherRegion, cloud: Double, wind: Double, timeOfDay: Double): Double {
    // Damp but not raining, calm, and before dawn - which is hour 6 of 24, so 0.25 of the day.
    val damp = cloud.coerceIn(0.0, 1.0)
    val calm = (1.0 - wind / params.sandstormWind).coerceIn(0.0, 1.0)
    val preDawn = 0.5 + 0.5 * cos(2.0 * Math.PI * (timeOfDay - PRE_DAWN_PEAK))
    val maritime = 1.0 - region.continentality
    return damp * calm * preDawn * (FOG_MARITIME_FLOOR + (1.0 - FOG_MARITIME_FLOOR) * maritime)
  }

  private data class Wind(val speed: Double, val direction: Double)

  /**
   * The surface wind: a prevailing bearing off the latitude, backing and veering as fronts pass over.
   *
   * ### The veer, and the two places it deliberately is not applied
   *
   * The bearing used to be `Winds.directionAt(latitude)` alone - a pure function of latitude, so **constant in
   * time**. See [WeatherParams.windVeerRadians] for what that cost. The veer is its own channel on the
   * synoptic wavelength, so it turns on the same timescale the gust strengthens on: one front, one change in
   * the weather.
   *
   * **Not [Winds.directionAt]'s own `seasonalShift`.** That is the physically right knob and it is the
   * *monsoon* knob - it moves the Hadley/Ferrel band boundaries, so it belongs to `ClimateStage`'s
   * precipitation sweep. Turning it on here would put a seasonal wind reversal in the weather without one in
   * the climate that produced the rainfall the weather is sampled against.
   *
   * **Not [drifted]'s advection vector**, which still uses the unveered prevailing. That vector is the
   * direction the *pattern* travels, and wobbling it moves the whole climatology - the measured percentages in
   * [WeatherParams]' own KDoc and the rank correlation `WeatherClimatologyTest` pins are both computed off it.
   * The wind a player feels and the direction the front travels are two different quantities, and only the
   * first one was wrong.
   */
  private fun windAt(region: WeatherRegion, dayOfWorld: Double): Wind {
    val prevailing = Winds.directionAt(region.latitude)
    val gust = drifted(windSeed, region, dayOfWorld, synopticWavelength, params.synopticPeriodDays)
    // Centred on zero, so the veer adds no net rotation and the mean bearing over a year is still the
    // latitude's own. `WindDirectionTest` asserts exactly that.
    val veer = (drifted(veerSeed, region, dayOfWorld, synopticWavelength, params.synopticPeriodDays) - 0.5) *
        2.0 * params.windVeerRadians
    return Wind(
      speed = BASE_WIND + gust * GUST_WIND,
      direction = Math.atan2(prevailing.y, prevailing.x) + veer
    )
  }

  /**
   * Where the tornado is.
   *
   * O(1), like everything else here, and it is what makes [WeatherKind.TORNADO] a thing a player can run from
   * rather than a status line. Drawn from the region and the day, so it does not teleport within one storm.
   */
  private fun hazardAt(region: WeatherRegion, dayOfWorld: Double): WeatherState.Hazard {
    val day = Math.floor(dayOfWorld).toLong()
    val angle = GenRng.hashUnit(windSeed, region.index.toLong(), day) * 2.0 * Math.PI
    val reach = GenRng.hashUnit(severitySeed, region.index.toLong(), day + 1) * HAZARD_REACH
    return WeatherState.Hazard(
      position = Vec2d(
        region.centre.x + Math.cos(angle) * reach,
        region.centre.y + Math.sin(angle) * reach
      ),
      radiusMetres = HAZARD_RADIUS
    )
  }

  /** The combined wetness channel, in `[0,1]`. */
  private fun channel(salt: Long, region: WeatherRegion, dayOfWorld: Double): Double =
    drifted(salt, region, dayOfWorld, synopticWavelength, params.synopticPeriodDays)

  /**
   * One advected fbm channel, squashed to `[0,1]`.
   *
   * The squash is a **measured** logistic rather than `(n + 1) / 2`: fbm of gradient noise is not uniform and
   * not Gaussian, and its distribution moves with the octave count. `WeatherFieldTest` fits and pins
   * [SQUASH_GAIN]; guessing it silently biases every climatology built on top.
   */
  private fun drifted(
    salt: Long,
    region: WeatherRegion,
    dayOfWorld: Double,
    wavelength: Double,
    periodDays: Double
  ): Double {
    val prevailing = Winds.directionAt(region.latitude)
    val speed = wavelength / periodDays
    val x = (region.centre.x - prevailing.x * speed * dayOfWorld) / wavelength
    val y = (region.centre.y - prevailing.y * speed * dayOfWorld) / wavelength

    val raw = Noise.fbm(salt, x, y, OCTAVES)
    return uniform(raw)
  }

  companion object {
    /** Bestia days in a year. Mirrors the runtime calendar; see `WeatherRegion.DAYS_PER_QUARTER`. */
    const val DAYS_PER_YEAR = 120L

    private const val OCTAVES = 3

    /**
     * The **measured cumulative distribution** of three-octave gradient-noise fbm, sampled at 33 evenly
     * spaced points across `[-1, 1]`.
     *
     * This is the squash, and it is a table rather than a formula because fbm's distribution is not one. Two
     * earlier attempts are worth recording: `(n + 1) / 2` left nine tenths of every region's days inside the
     * middle third of the range, and a fitted logistic at gain 3.6 put the 5th percentile at 0.232 and made
     * `tornadoSeverity = 0.93` **literally unreachable** - measured over two million samples, the field never
     * once got there.
     *
     * The distribution is a property of `Noise.fbm` at three octaves, not of any world, so this is measured
     * once and pinned. `WeatherFieldTest` asserts the result is uniform to within five points, which is what
     * makes every threshold in [WeatherParams] mean its own face value.
     *
     * Real range is about `[-0.72, +0.73]`; the flat runs at both ends are that headroom.
     */
    private val FBM_CDF = doubleArrayOf(
      0.00000, 0.00000, 0.00000, 0.00000, 0.00000, 0.00001,
      0.00013, 0.00085, 0.00370, 0.01167, 0.02942, 0.06182,
      0.11152, 0.18076, 0.26956, 0.37647, 0.49995, 0.62343,
      0.73010, 0.81889, 0.88821, 0.93794, 0.97035, 0.98828,
      0.99632, 0.99917, 0.99988, 0.99999, 1.00000, 1.00000,
      1.00000, 1.00000, 1.00000
    )

    private const val FREEZING = 0.0

    /** Fraction of a mana storm's severity that reads as intensity when nothing wetter is happening. */
    private const val SEVERITY_AS_INTENSITY = 0.9

    private const val PEAK_MANA_WEIGHT = 0.6

    private const val TORNADO_POTENTIAL = 0.45
    private const val CONVECTION_COLD = 4.0
    private const val CONVECTION_WARM = 26.0
    private const val CONVECTION_RELIEF = 900.0

    /** Fraction of the day convection peaks at. Late afternoon, after the 0.625 solar noon. */
    private const val AFTERNOON_PEAK = 0.68

    /** Fraction of the day fog peaks at. Just before the hour-6 dawn. */
    private const val PRE_DAWN_PEAK = 0.19

    private const val FOG_MARITIME_FLOOR = 0.35

    private const val BASE_WIND = 3.0
    private const val GUST_WIND = 22.0

    /** How far from a region's centre a tornado can be, in metres. Under a region's own half-width. */
    private const val HAZARD_REACH = 6_000.0
    private const val HAZARD_RADIUS = 400.0

    private const val WET_SALT = 0x5765744368616EL
    private const val AIRMASS_SALT = 0x41697252654D61L
    private const val SEVERITY_SALT = 0x5365765274794CL
    private const val WIND_SALT = 0x57696E6447757AL

    /** Its own salt, so the bearing does not turn in lockstep with the gust strengthening. */
    private const val VEER_SALT = 0x576E6456656572L

    /**
     * Flattens a `[-1,1]` fbm sample into a uniform `[0,1]`, through [FBM_CDF].
     *
     * Interpolated rather than truncated, for `Tables.linear`'s own reason: a staircase here would ring at the
     * table's pitch, and a weather field that rings is a weather field with a period in it nothing designed.
     */
    fun uniform(raw: Double): Double {
      val position = (raw + 1.0) / 2.0 * (FBM_CDF.size - 1)
      return Tables.linear(FBM_CDF, position).coerceIn(0.0, 1.0)
    }

    /** Builds the model for a generated world. */
    fun of(generated: GeneratedWorld, params: WeatherParams = WeatherParams()): WeatherModel =
      WeatherModel(
        regions = WeatherRegions.of(generated),
        config = generated.config,
        params = params,
        seed = generated.config.seed
      )
  }
}
