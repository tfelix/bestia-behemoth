package net.bestia.worldgen.climate

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Noise
import net.bestia.worldgen.geo.TectonicsStage
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** Tuning for [ClimateStage]. Belongs in a data file eventually; see [net.bestia.worldgen.geo.TectonicsParams]. */
data class ClimateParams(

  /** Latitude at the northern edge of the map. The world is a band of the globe, not a whole one. */
  val polewardLatitude: Double = 68.0,

  val equatorTemperature: Double = 28.0,
  val poleTemperature: Double = -24.0,

  /** Degrees lost per kilometre of elevation. */
  val lapseRate: Double = 6.2,

  /**
   * Distance over which the ocean's moderating influence decays, in metres.
   *
   * Scaled down on a small world, because otherwise every cell is coastal. At 420 km a 128 km world is entirely
   * within the sea's reach, so its seasonal temperature range is flat everywhere and the biome classifier - which
   * leans on that range to tell a continental interior from a maritime one - has nothing to work with and returns
   * one biome over the whole map. Shrinking it is unphysical and is exactly what buys the variety.
   */
  val maritimeRange: Double = 420_000.0,

  /**
   * Rain per metre of forced ascent, as a fraction of the moisture present.
   *
   * Lowered from 0.0021, where it was not a coefficient so much as a switch. `rain = moisture * this * rise`
   * reaches the whole of the moisture at a rise of 476 m, and `rain` is then clamped to what is there - so on
   * a world with 4 km climate cells and 4,500 m peaks, the *first* range a wind met stripped the air to
   * nothing, and since nothing evaporates over land it stayed at nothing for the rest of the continent. Every
   * interior was a rain shadow of one mountain, which is how a world ends up 29% desert and 21% cold desert
   * with a green fringe. At 0.0009 a kilometre of ascent takes most of the moisture but not all of it, which
   * is what a range does.
   */
  val orographicCoefficient: Double = 0.0009,

  /** Rain per cell of travel from ordinary convection, as a fraction of the moisture present. */
  val convectiveRate: Double = 0.016,

  /**
   * How much convective rain survives on the lee side of a crest. This is the rain shadow.
   *
   * Raised from 0.22 when the world became land-dominated and mountainous. A rain shadow is a feature; a world
   * where *everywhere* is behind a crest is a desert. With plate boundaries every twenty-five kilometres there
   * is no longer such a thing as an interior that is not downwind of a range, so the suppression compounds
   * pass after pass and the continents came out as sand with a green fringe. At 0.34 a single range still casts
   * an unmistakable shadow - which is the thing worth keeping - without the second and third ranges behind it
   * finishing the job.
   */
  val leeSuppression: Double = 0.34,

  /** Fraction of the moisture deficit made up per cell of travel over water. */
  val evaporationRate: Double = 0.10,

  /**
   * The same, per cell of travel over **land**. Continental moisture recycling.
   *
   * Zero, as it was, says that once air crosses a shoreline the only thing that can happen to its water is
   * that it falls out. That is not how a continent works: rain that lands on vegetated ground largely goes
   * back up as evapotranspiration and falls again further downwind - the Amazon recycles something like half
   * its rainfall that way, and it is the reason a continental interior is habitable at all rather than being
   * a desert as a matter of arithmetic. Without the term, the model's interiors were deserts as a matter of
   * arithmetic.
   *
   * A third of the ocean rate, because land gives its water back more slowly than an ocean surface does, and
   * because this must not become a second ocean - too high and the wind arrives at the far coast wetter than
   * it left the near one, which erases rain shadows entirely and with them the reason deserts sit where they
   * do.
   */
  val landEvaporationRate: Double = 0.050,

  /**
   * Seasonal passes, one per stored seasonal precipitation layer.
   *
   * Four rather than two, because the passes now have a reader. Two is the cheapest number that produces
   * a monsoon and it was the right number for as long as only the annual sum and a scalar spread survived;
   * a consumer asking "how much rain in month seven" needs the two equinoxes as well, or it is interpolating
   * a year from its two extremes and getting a triangle wave.
   *
   * Not twelve. Four control points and a periodic spline describe the annual curve to well within the
   * accuracy of everything upstream of it - see [SeasonalPrecipitation.atMonth] - and twelve layers would
   * be twelve times the memory and twelve advection sweeps to store what four already say.
   */
  val seasons: Int = 4,

  /** Degrees the wind belts migrate between the seasonal extremes. */
  val seasonalShift: Double = 6.0,

  /**
   * Months by which the belt migration lags the thermal cycle.
   *
   * Without a lag the two seasonal inputs are the same sine, so the two equinox passes get identical belt
   * positions *and* identical temperatures and produce two identical fields - which is the failure mode of
   * storing four seasons instead of two, and would put a duplicate layer on disk while claiming to describe
   * spring and autumn.
   *
   * A lag is the physically real distinction rather than a device to break the tie: a monsoon's wettest
   * month comes *after* its hottest, because the cross-equatorial gradient the circulation responds to has
   * the ocean's thermal inertia behind it. India is hottest in May and wettest in July. One month over a
   * twelve-month year puts the two equinoxes at the same temperature and opposite belt positions, which is
   * exactly what makes them different seasons.
   */
  val monsoonLagMonths: Double = 1.0,

  /**
   * Blur passes applied to each seasonal precipitation field.
   *
   * The advection sweep runs along rows, so without lateral mixing the result acquires faint
   * horizontal striping - a numerical artefact of the sweep direction, not a feature of the climate.
   * Two or three passes of eddy mixing removes it and is defensible as physics rather than as a fudge.
   */
  val mixingPasses: Int = 3,

  /**
   * Mean annual precipitation over the whole world in millimetres, which the field is scaled to.
   *
   * **It only became a lever once the field stopped being mostly zero.** Measured on the old model, raising it
   * from 880 to 1150 moved the biome mix by a single percentage point, and this KDoc used to say so and send
   * the reader elsewhere. That was a true measurement of a broken distribution: with [orographicCoefficient]
   * at 0.0021 and no [landEvaporationRate], most of the land was at *exactly* zero rain, and scaling zero by
   * 1.3 is still zero. Once air can cross a range and a continent can recycle its own water, the field has a
   * spread to scale and this moves the mix as you would expect - 1250 to 1420 was worth two points of forest.
   *
   * Note that a change here cancels in [net.bestia.worldgen.geo.ErosionStage] and
   * [net.bestia.worldgen.hydro.HydrologyStage], which both normalise by the field's own mean by design. So it
   * moves biomes without moving the landscape, and it is the cheapest thing to reach for when a world is green
   * enough in shape but not in classification.
   *
   * 1850 is well above Earth's ~1000 mm. Deliberate: this world is meant to be a place with forests in it.
   * Measured across three seeds it puts closed forest plus grassland at 55%, 45% and 51% of the land.
   */
  val meanPrecipitation: Double = 1850.0
) {
  init {
    require(polewardLatitude in 1.0..90.0) { "polewardLatitude must be in (0,90]" }
    // Pinned rather than merely positive: each season is stored as its own named layer, and a layer name is
    // an on-disk contract. Three seasons would emit three layers against four declared outputs, and
    // `verifyOutputs` would report a missing `precipitation_winter` - true, and no help at all in finding
    // the parameter that caused it.
    require(seasons == SeasonalPrecipitation.COUNT) {
      "seasons must be exactly ${SeasonalPrecipitation.COUNT}, one per stored layer, was $seasons"
    }
    require(meanPrecipitation > 0.0) { "meanPrecipitation must be positive" }
  }
}

/**
 * Stage 2: temperature, continentality, and precipitation by orographic advection.
 *
 * Runs at a coarser resolution than the heightfield on purpose - advection over a 1 km grid is wasted
 * work, because the process being modelled has a characteristic scale of hundreds of kilometres. The
 * elevation it needs is sampled down from the fine grid, and everything downstream samples the results
 * back up bicubically. That the two stages need not agree on a resolution is the entire point of
 * resolution being a per-stage property.
 */
class ClimateStage(
  override val resolution: Resolution = Resolution.FOUR_KILOMETRE,
  private val params: ClimateParams = ClimateParams()
) : Stage {

  override val id = ID

  // 2: belt boundaries are blended rather than stepped, and the row sweep wraps on a wrapping world.
  // 3: four seasons rather than two, kept as layers; the sweep's temperature is seasonal and hemisphere-
  //    correct, and the belt shift is sinusoidal with a monsoon lag behind it.
  override val version = 3
  override val dependencies = listOf(TectonicsStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.TEMPERATURE),
    StageOutput.Raster(LayerId.TEMPERATURE_RANGE),
    StageOutput.Raster(LayerId.PRECIPITATION),
    StageOutput.Raster(LayerId.PRECIPITATION_SEASONALITY),
    StageOutput.Raster(LayerId.DISTANCE_TO_OCEAN)
  ) + SeasonalPrecipitation.LAYERS.map { StageOutput.Raster(it) }

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel
    val elevation = Grid.resampled(ctx.layers.float(LayerId.BEDROCK_ELEVATION), region)

    val bounds = region.toWorld()
    val latitudes = DoubleArray(region.height) { y ->
      latitudeOf((y + 0.5) / region.height, params.polewardLatitude)
    }

    val oceanDistance = DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
      elevation.data[elevation.index(x, y)] <= seaLevel
    }
    capUnreachable(oceanDistance, max(bounds.width, bounds.height))

    // Shrunk on a small world: at its reference value every cell of a 128 km world is within the ocean's reach,
    // which flattens the seasonal range everywhere and leaves the biome classifier unable to tell a continental
    // interior from a coast. See WorldConfig.detailScale.
    val maritimeRange = ctx.config.scaleByLength(params.maritimeRange)
    val temperature = temperatureField(ctx, region, elevation, latitudes, oceanDistance, seaLevel, maritimeRange)
    val range = temperatureRange(latitudes, oceanDistance, region, maritimeRange)
    val seasonal =
      seasonalPrecipitation(elevation, temperature, range, latitudes, seaLevel, region, ctx.config.wrapX)

    val precipitation = Grid(region.width, region.height)
    for (season in seasonal) {
      for (i in precipitation.data.indices) precipitation.data[i] += season.data[i]
    }

    // Before the seasonality index, because that index is scale-invariant and it is cheaper to reason about
    // one ordering than to prove it does not matter.
    normaliseToMean(precipitation, seasonal, params.meanPrecipitation)

    val layers = mutableListOf(
      temperature.toLayer(LayerId.TEMPERATURE, region),
      range.toLayer(LayerId.TEMPERATURE_RANGE, region),
      precipitation.toLayer(LayerId.PRECIPITATION, region),
      seasonality(seasonal, region).toLayer(LayerId.PRECIPITATION_SEASONALITY, region),
      oceanDistance.toLayer(LayerId.DISTANCE_TO_OCEAN, region)
    )

    // One layer per season, in the order SeasonalPrecipitation reads them back. `seasons` is required to be
    // exactly SeasonalPrecipitation.COUNT below, so this cannot silently emit the wrong number and trip
    // verifyOutputs with a message about a missing layer rather than about the parameter that caused it.
    for ((season, grid) in seasonal.withIndex()) {
      layers.add(grid.toLayer(SeasonalPrecipitation.LAYERS[season], region))
    }

    return StageResult.of(*layers.toTypedArray())
  }

  /**
   * Mean annual temperature: a latitude curve, minus the lapse rate, pulled toward the local sea
   * surface temperature near the coast.
   */
  private fun temperatureField(
    ctx: GenContext,
    region: CellRegion,
    elevation: Grid,
    latitudes: DoubleArray,
    oceanDistance: Grid,
    seaLevel: Double,
    maritimeRange: Double
  ): Grid {
    val metres = region.resolution.metresPerCell
    val noiseSeed = GenRng.mix64(ctx.seed xor TEMPERATURE_SALT)
    val grid = Grid(region.width, region.height)

    for (y in 0 until region.height) {
      val seaSurface = seaLevelTemperature(latitudes[y])
      val worldY = (region.minY + y + 0.5) * metres

      for (x in 0 until region.width) {
        val i = grid.index(x, y)
        val above = max(0.0, elevation.data[i] - seaLevel)

        var t = seaSurface - params.lapseRate * above / 1000.0

        // Maritime moderation: the sea is a flywheel, so coasts sit closer to the ocean temperature
        // than their elevation alone would suggest.
        val maritime = exp(-oceanDistance.data[i] / maritimeRange)
        t += (seaSurface - t) * maritime * MARITIME_PULL

        t += Noise.fbm(
          noiseSeed,
          (region.minX + x + 0.5) * metres / TEMPERATURE_WAVELENGTH,
          worldY / TEMPERATURE_WAVELENGTH,
          3
        ) * TEMPERATURE_NOISE

        grid.data[i] = t
      }
    }

    return grid
  }

  /**
   * Summer-to-winter swing.
   *
   * Grows with latitude - the tropics barely have seasons - and with continentality, because the
   * further you are from the sea the less there is to damp the swing. Siberia and Ireland sit at the
   * same latitude and differ by thirty degrees of annual range, and that difference decides which
   * biome each of them gets.
   */
  private fun temperatureRange(
    latitudes: DoubleArray,
    oceanDistance: Grid,
    region: CellRegion,
    maritimeRange: Double
  ): Grid {
    val grid = Grid(region.width, region.height)

    for (y in 0 until region.height) {
      val byLatitude = BASE_RANGE + LATITUDE_RANGE * (abs(latitudes[y]) / 90.0).pow(1.2)

      for (x in 0 until region.width) {
        val i = grid.index(x, y)
        val continentality = 1.0 - exp(-oceanDistance.data[i] / maritimeRange)
        grid.data[i] = byLatitude * (MARITIME_RANGE_FLOOR + (1.0 - MARITIME_RANGE_FLOOR) * continentality)
      }
    }

    return grid
  }

  /**
   * One precipitation field per season, from the advection sweep described in the architecture
   * document: carry moisture along the wind, evaporate over water, rain it out where the air is forced
   * to rise, and suppress convection on the descent.
   *
   * Sweeping along rows means the meridional component of the wind transports nothing, which the
   * mixing pass afterwards partly makes up for. The trade is deliberate: a proper two-dimensional
   * semi-Lagrangian advection would be a solver rather than a loop, and the thing it would buy -
   * moisture arriving diagonally - is second order next to getting the rain shadows on the right side.
   *
   * Each row is swept in **both** directions and mixed by [Winds.eastwardShare] wherever the row sits inside
   * a wind-belt boundary. Picking one direction per row from the sign of the wind put a full-width
   * discontinuity into the field at every belt boundary, which is the artefact that KDoc describes.
   */
  private fun seasonalPrecipitation(
    elevation: Grid,
    temperature: Grid,
    range: Grid,
    latitudes: DoubleArray,
    seaLevel: Double,
    region: CellRegion,
    cyclic: Boolean
  ): List<Grid> {
    val out = ArrayList<Grid>(params.seasons)
    val eastward = DoubleArray(region.width)
    val westward = DoubleArray(region.width)

    for (season in 0 until params.seasons) {
      // Sinusoidal rather than a linear ramp between the extremes. A ramp is right for two seasons and wrong
      // for four: `shift * (2*s/(n-1) - 1)` gives -6, -2, +2, +6, which is two *distinct* near-equinox belt
      // positions, when physically spring and autumn sit at the same one. A sine gives 0, +6, 0, -6 and puts
      // the solstices at the extremes where they belong.
      val phase = TAU * season / params.seasons

      // The belt migration lags the thermal cycle - see monsoonLagMonths. This is what keeps the two
      // equinoxes from being the same field: equal temperature, opposite belt position.
      val shift = params.seasonalShift * kotlin.math.sin(phase - monsoonLag())

      // How far this season sits from the annual mean, as a fraction of the local summer-to-winter swing.
      // Signed per hemisphere at the row, not here.
      val warming = 0.5 * kotlin.math.sin(phase)

      val precip = Grid(region.width, region.height)

      for (y in 0 until region.height) {
        val share = Winds.eastwardShare(latitudes[y], shift)
        val row = precip.index(0, y)

        // The hemispheres are half a year apart, so the same orbital phase warms one and cools the other.
        // Without this the whole world would have its summer at once and a southern monsoon would arrive in
        // the wrong half of the year - which is the thing four fields exist to express.
        val seasonalWarming = if (latitudes[y] >= 0.0) warming else -warming

        // Skipping the sweep that contributes nothing keeps the deep tropics and the polar cells at exactly
        // one sweep, which is what they had before this became a blend.
        if (share < 1.0) {
          sweepRow(elevation, temperature, range, seasonalWarming, y, -1, seaLevel, region, cyclic, westward)
        }
        if (share > 0.0) {
          sweepRow(elevation, temperature, range, seasonalWarming, y, +1, seaLevel, region, cyclic, eastward)
        }

        for (x in 0 until region.width) {
          precip.data[row + x] = when {
            share <= 0.0 -> westward[x]
            share >= 1.0 -> eastward[x]
            else -> westward[x] + (eastward[x] - westward[x]) * share
          }
        }
      }

      precip.blur(params.mixingPasses)
      out.add(precip)
    }

    return out
  }

  /** The belt migration's lag behind the thermal cycle, in radians. See [ClimateParams.monsoonLagMonths]. */
  private fun monsoonLag(): Double = TAU * params.monsoonLagMonths / MONTHS_PER_YEAR

  /**
   * The advection sweep along one row, in one direction, into [out].
   *
   * [cyclic] runs the row twice and records only the second lap. The sweep starts with dry air, so without
   * it the column it happens to start at is the one column in the row with no upwind fetch at all - it always
   * receives exactly zero rain, and since rows alternate direction the result is a dry stripe down both map
   * edges. On a world that wraps in x there is no such column: the air that leaves the east edge is the air
   * that arrives at the west one. The spin-up lap is what lets the row find that equilibrium. Both edges of
   * the map are forced ocean, so the seam the lap crosses is open water on both sides.
   */
  private fun sweepRow(
    elevation: Grid,
    temperature: Grid,
    range: Grid,
    seasonalWarming: Double,
    y: Int,
    step: Int,
    seaLevel: Double,
    region: CellRegion,
    cyclic: Boolean,
    out: DoubleArray
  ) {
    val width = region.width
    val start = if (step > 0) 0 else width - 1
    val row = elevation.index(0, y)

    var moisture = 0.0
    var previousElevation = elevation.data[row + start]

    val laps = if (cyclic) 2 else 1
    for (lap in 0 until laps) {
      val record = lap == laps - 1

      for (n in 0 until width) {
        val x = Math.floorMod(start + step * n, width)
        val i = row + x
        val z = elevation.data[i]

        // The season's temperature, not the annual mean. TEMPERATURE_RANGE already *is* the summer-to-winter
        // swing, so half of it either side of the mean is the seasonal excursion with no new constant needed.
        //
        // This is what makes the four fields genuinely different rather than four wind directions over one
        // climate: it feeds Winds.capacity, which drives evaporation and the convective term, so a warm
        // season both picks up more water over the sea and rains more of it out over the land. Continentality
        // comes along for free - the range is already larger inland, so an interior swings harder than a
        // coast at the same latitude, which is why a monsoon is a continental phenomenon.
        val seasonal = temperature.data[i] + range.data[i] * seasonalWarming
        val capacity = Winds.capacity(seasonal)

        // Saturating: air over the middle of an ocean does not keep gaining moisture forever. Land gives its
        // water back too, more slowly - see landEvaporationRate.
        val evaporation = if (z <= seaLevel) params.evaporationRate else params.landEvaporationRate
        moisture += (capacity - moisture).coerceAtLeast(0.0) * evaporation

        val rise = z - previousElevation
        var rain = 0.0
        if (rise > 0.0) {
          rain += moisture * params.orographicCoefficient * rise
        }

        val descending = rise < 0.0
        rain += moisture * params.convectiveRate *
            (capacity / Winds.capacity(REFERENCE_TEMPERATURE)) *
            (if (descending) params.leeSuppression else 1.0)

        rain = min(rain, moisture)
        moisture -= rain
        if (record) out[x] = rain

        previousElevation = z
      }
    }
  }

  /**
   * 0 where the seasons are alike, approaching 1 where all the rain falls in one of them.
   *
   * Still the wettest-against-driest spread now that there are four seasons rather than two, and **a summed
   * concentration index was tried here and reverted.** The argument for one is real: min-max reads only the
   * extremes, so on paper it cannot see the two middle seasons at all. The argument against is a measurement.
   *
   * A concentration index - summed absolute deviation from the even share, over its own maximum - is the
   * natural generalisation, and it agrees with this expression exactly when `n` is 2. It is nonetheless
   * *systematically lower* at four, because two of the four seasons now sit near the mean and contribute
   * almost nothing to the sum while still counting in the normaliser. `BiomeStage` thresholds on this layer
   * and its thresholds were tuned against the old magnitudes, so the whole world reclassified: measured on
   * the 512 km reference world, green land went from 64% to 68%, shrubland from 9% to 5%, and **tropical
   * seasonal forest disappeared from the world entirely** while tundra appeared at 6%. Keeping min-max gives
   * 63% - the reference world, unmoved.
   *
   * That is a retune of the biome classifier wearing a definition change's clothes, and it belongs in a
   * change that measures biomes rather than in one that adds layers.
   *
   * **And the blindness is mostly theoretical for this model.** The annual cycle here is one sine, so the
   * extremes are the two solstices and the equinoxes lie between them - which makes min-max over four seasons
   * the same quantity as min-max over two, and is precisely why adopting it left the reference world alone.
   * It would genuinely lose information for a *bimodal* year, two wet seasons either side of a dry one, which
   * a single sinusoidal belt migration cannot produce. If the seasonal cycle ever gains a second harmonic,
   * revisit this - and retune `BiomeStage` in the same change.
   */
  private fun seasonality(seasonal: List<Grid>, region: CellRegion): Grid {
    val grid = Grid(region.width, region.height)
    if (seasonal.size < 2) return grid

    for (i in grid.data.indices) {
      var lowest = Double.MAX_VALUE
      var highest = 0.0
      var total = 0.0
      for (season in seasonal) {
        val v = season.data[i]
        lowest = min(lowest, v)
        highest = max(highest, v)
        total += v
      }
      grid.data[i] = if (total <= 1e-12) 0.0 else (highest - lowest) / (highest + lowest + 1e-12)
    }

    return grid
  }

  /**
   * Rescales the annual field to [target], and the seasonal fields it was summed from by the same factor.
   *
   * **The factor has to reach the seasonal fields or the layers contradict each other.** This used to scale
   * only the annual sum and compute its factor locally, which was correct while the seasonal grids were
   * discarded a line later and is a bug the moment they are stored: `PRECIPITATION` would be in millimetres
   * of a calibrated year and its four parts in raw sweep units, so they would not add up and every claim the
   * four layers make about being a decomposition would be false.
   *
   * Both are scaled here rather than the caller applying a returned factor, because the degenerate case has
   * no factor to return. A world with no rain anywhere is legitimate - no ocean, or ice to the equator - and
   * gets a flat field; no multiplier turns four zero fields into four that sum to it, so they are filled
   * directly and the invariant holds in that case too.
   */
  private fun normaliseToMean(annual: Grid, seasonal: List<Grid>, target: Double) {
    val mean = annual.mean()

    if (mean <= 1e-12) {
      annual.fill(target)
      for (season in seasonal) season.fill(target / seasonal.size)
      return
    }

    val factor = target / mean
    for (i in annual.data.indices) annual.data[i] *= factor
    for (season in seasonal) {
      for (i in season.data.indices) season.data[i] *= factor
    }
  }

  /**
   * A world with no ocean at all leaves [DistanceTransform] reporting `Double.MAX_VALUE`, which becomes
   * `Infinity` once written into a float layer and poisons every consumer downstream. A seed like that
   * is legitimate - unlikely, but legitimate - so cap the distance at the size of the world rather
   * than treating it as a failure.
   */
  private fun capUnreachable(grid: Grid, cap: Double) {
    for (i in grid.data.indices) {
      if (grid.data[i] > cap) grid.data[i] = cap
    }
  }

  private fun seaLevelTemperature(latitude: Double): Double {
    val poleward = (abs(latitude) / 90.0).coerceIn(0.0, 1.0)
    return params.equatorTemperature +
        (params.poleTemperature - params.equatorTemperature) * poleward.pow(LATITUDE_EXPONENT)
  }

  companion object {
    val ID = StageId("climate")

    /**
     * Latitude in degrees at a fractional position from the south edge of the world to the north.
     *
     * A linear ramp, which is the whole of this pipeline's geography: it is why [net.bestia.worldgen.core.WorldConfig.wrapY]
     * is a discontinuity rather than a join, and it is the one place the mapping is written down.
     *
     * Public because a downstream stage that wants the prevailing wind needs it - the noxious quarter of a
     * town goes downwind, and the alternative was for that stage to re-derive latitude with its own idea of
     * where the pole is, which is precisely how two stages come to disagree about the same world.
     *
     * @param polewardLatitude latitude at the world's north edge. Callers outside this stage cannot see the
     *   configured value and should pass the default; the consequence of a mismatch is a craft district on
     *   the wrong side of a town, which is cosmetic - not terrain that disagrees with itself.
     */
    fun latitudeOf(northwards: Double, polewardLatitude: Double = ClimateParams().polewardLatitude): Double =
      (northwards * 2.0 - 1.0) * polewardLatitude

    private const val TEMPERATURE_SALT = 0x3E7A1C95D284B60L

    /** How strongly a coastal cell is pulled back toward sea surface temperature. */
    private const val MARITIME_PULL = 0.35

    /** Annual range at the equator, in degrees. */
    private const val BASE_RANGE = 3.0

    /** Additional annual range at the pole, in degrees. */
    private const val LATITUDE_RANGE = 34.0

    /** Fraction of the latitude range that survives right on the coast. */
    private const val MARITIME_RANGE_FLOOR = 0.34

    /** Shape of the equator-to-pole temperature curve; above 1 keeps the tropics broad. */
    private const val LATITUDE_EXPONENT = 1.35

    private const val TEMPERATURE_WAVELENGTH = 160_000.0
    private const val TEMPERATURE_NOISE = 1.8

    private const val REFERENCE_TEMPERATURE = 20.0

    /** One full turn. The seasonal cycle is periodic, so every phase here is a fraction of it. */
    private const val TAU = 2.0 * Math.PI

    /**
     * Months in this world's year, for turning [ClimateParams.monsoonLagMonths] into a phase.
     *
     * Twelve because the consumer of the seasonal fields asks for a month - see
     * [SeasonalPrecipitation.atMonth] - and a lag expressed in months has to mean the same thing in both
     * places or the curve is phase-shifted against the fields it interpolates.
     */
    const val MONTHS_PER_YEAR = 12.0
  }
}
