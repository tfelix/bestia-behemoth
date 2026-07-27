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

  /** Distance over which the ocean's moderating influence decays, in metres. */
  val maritimeRange: Double = 420_000.0,

  /** Rain per metre of forced ascent, as a fraction of the moisture present. */
  val orographicCoefficient: Double = 0.0021,

  /** Rain per cell of travel from ordinary convection, as a fraction of the moisture present. */
  val convectiveRate: Double = 0.016,

  /** How much convective rain survives on the lee side of a crest. This is the rain shadow. */
  val leeSuppression: Double = 0.22,

  /** Fraction of the moisture deficit made up per cell of travel over water. */
  val evaporationRate: Double = 0.10,

  /**
   * Seasonal passes. Two - a summer and a winter wind field - is the cheapest number that produces a
   * monsoon, and the seasonality it yields is what biomes actually need. More passes buy detail that
   * nothing downstream currently reads.
   */
  val seasons: Int = 2,

  /** Degrees the wind belts migrate between the seasonal extremes. */
  val seasonalShift: Double = 9.0,

  /**
   * Blur passes applied to each seasonal precipitation field.
   *
   * The advection sweep runs along rows, so without lateral mixing the result acquires faint
   * horizontal striping - a numerical artefact of the sweep direction, not a feature of the climate.
   * Two or three passes of eddy mixing removes it and is defensible as physics rather than as a fudge.
   */
  val mixingPasses: Int = 3,

  /** Mean annual precipitation over the whole world in millimetres, which the field is scaled to. */
  val meanPrecipitation: Double = 880.0
) {
  init {
    require(polewardLatitude in 1.0..90.0) { "polewardLatitude must be in (0,90]" }
    require(seasons >= 1) { "seasons must be at least 1" }
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
  override val version = 1
  override val dependencies = listOf(TectonicsStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.TEMPERATURE),
    StageOutput.Raster(LayerId.TEMPERATURE_RANGE),
    StageOutput.Raster(LayerId.PRECIPITATION),
    StageOutput.Raster(LayerId.PRECIPITATION_SEASONALITY),
    StageOutput.Raster(LayerId.DISTANCE_TO_OCEAN)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel
    val elevation = Grid.resampled(ctx.layers.float(LayerId.BEDROCK_ELEVATION), region)

    val bounds = region.toWorld()
    val latitudes = DoubleArray(region.height) { y ->
      val northwards = (y + 0.5) / region.height
      (northwards * 2.0 - 1.0) * params.polewardLatitude
    }

    val oceanDistance = DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
      elevation.data[elevation.index(x, y)] <= seaLevel
    }
    capUnreachable(oceanDistance, max(bounds.width, bounds.height))

    val temperature = temperatureField(ctx, region, elevation, latitudes, oceanDistance, seaLevel)
    val range = temperatureRange(latitudes, oceanDistance, region)
    val seasonal = seasonalPrecipitation(elevation, temperature, latitudes, seaLevel, region)

    val precipitation = Grid(region.width, region.height)
    for (season in seasonal) {
      for (i in precipitation.data.indices) precipitation.data[i] += season.data[i]
    }
    scaleToMean(precipitation, params.meanPrecipitation)

    return StageResult.of(
      temperature.toLayer(LayerId.TEMPERATURE, region),
      range.toLayer(LayerId.TEMPERATURE_RANGE, region),
      precipitation.toLayer(LayerId.PRECIPITATION, region),
      seasonality(seasonal, region).toLayer(LayerId.PRECIPITATION_SEASONALITY, region),
      oceanDistance.toLayer(LayerId.DISTANCE_TO_OCEAN, region)
    )
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
    seaLevel: Double
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
        val maritime = exp(-oceanDistance.data[i] / params.maritimeRange)
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
  private fun temperatureRange(latitudes: DoubleArray, oceanDistance: Grid, region: CellRegion): Grid {
    val grid = Grid(region.width, region.height)

    for (y in 0 until region.height) {
      val byLatitude = BASE_RANGE + LATITUDE_RANGE * (abs(latitudes[y]) / 90.0).pow(1.2)

      for (x in 0 until region.width) {
        val i = grid.index(x, y)
        val continentality = 1.0 - exp(-oceanDistance.data[i] / params.maritimeRange)
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
   */
  private fun seasonalPrecipitation(
    elevation: Grid,
    temperature: Grid,
    latitudes: DoubleArray,
    seaLevel: Double,
    region: CellRegion
  ): List<Grid> {
    val out = ArrayList<Grid>(params.seasons)

    for (season in 0 until params.seasons) {
      // Seasons are laid out symmetrically about zero shift, so with two of them one is the summer
      // extreme and the other the winter extreme rather than both being the same.
      val shift = if (params.seasons == 1) {
        0.0
      } else {
        params.seasonalShift * (2.0 * season / (params.seasons - 1) - 1.0)
      }

      val precip = Grid(region.width, region.height)

      for (y in 0 until region.height) {
        val step = Winds.zonalSign(latitudes[y], shift)
        val start = if (step > 0) 0 else region.width - 1
        val end = if (step > 0) region.width else -1

        var moisture = 0.0
        var previousElevation = elevation.data[elevation.index(start, y)]

        var x = start
        while (x != end) {
          val i = precip.index(x, y)
          val z = elevation.data[i]
          val t = temperature.data[i]
          val capacity = Winds.capacity(t)

          if (z <= seaLevel) {
            // Saturating: air over the middle of an ocean does not keep gaining moisture forever.
            moisture += (capacity - moisture).coerceAtLeast(0.0) * params.evaporationRate
          }

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
          precip.data[i] = rain

          previousElevation = z
          x += step
        }
      }

      precip.blur(params.mixingPasses)
      out.add(precip)
    }

    return out
  }

  /** 0 where the seasons are alike, approaching 1 where all the rain falls in one of them. */
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

  /** Rescales in place so the field's mean is [target]. */
  private fun scaleToMean(grid: Grid, target: Double) {
    val mean = grid.mean()
    if (mean <= 1e-12) {
      grid.fill(target)
      return
    }
    val factor = target / mean
    for (i in grid.data.indices) grid.data[i] *= factor
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
  }
}
