package net.bestia.worldgen.climate

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.PointIndex
import net.bestia.worldgen.fields.PoissonDisk
import net.bestia.worldgen.pipeline.GeneratedWorld
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d

/**
 * The thirty-year average over one weather region: everything the weather model needs to know about a place.
 *
 * A **summary**, not a sample. The weather field decides how far today sits from normal; this is what normal
 * is here, and it is computed once from the climate layers rather than resampled per query.
 */
data class WeatherRegion(
  val index: Int,

  /** The region's Poisson seed point. Weather is evaluated here and applies to the whole cell. */
  val centre: Vec2d,

  /** Cells of the climate grid that fall in this region. Zero only for a region entirely off the grid. */
  val cellCount: Int,

  /** Millimetres in each of the four quarters, in `SeasonalPrecipitation.LAYERS` order. */
  val seasonalPrecipitation: DoubleArray,

  val meanTemperature: Double,

  /** Summer-to-winter swing in degrees. */
  val temperatureRange: Double,

  /** `0` on a coast, approaching `1` deep inland. Drives the diurnal swing. */
  val continentality: Double,

  val meanElevation: Double,

  /** p95 minus p5 of elevation, in metres. Convective potential and how much snow lies above the rain. */
  val relief: Double,

  /** Share of the region that is dry land. */
  val landShare: Double,

  /** Share that is sand or bare ground: what a sandstorm needs to lift. */
  val looseSurfaceShare: Double,

  /** Mean mana over the region, and the 95th percentile. See `WeatherField` for why both. */
  val meanMana: Double,
  val peakMana: Double,

  /** Latitude in degrees at the centre. Prevailing wind, and the hemisphere. */
  val latitude: Double
) {

  /**
   * Rainfall in millimetres per Bestia day at a fraction of the year.
   *
   * Through [SeasonalPrecipitation]'s own spline shape rather than a second interpolation: the four quarters
   * are values at their centres and the curve through them is periodic, so a linear read would put a corner
   * at the new year.
   */
  fun rainfallAt(yearProgress: Double): Double {
    val quarters = seasonalPrecipitation.size
    val t = yearProgress * quarters - 0.5
    val i = Math.floor(t).toInt()
    val frac = t - i

    fun at(index: Int) = seasonalPrecipitation[Math.floorMod(index, quarters)]

    val quarterly = net.bestia.worldgen.vector.Polyline.catmullRom(
      at(i - 1), at(i), at(i + 1), at(i + 2), frac
    )

    // A quarterly total spread over the Bestia days in a quarter.
    return (quarterly / DAYS_PER_QUARTER).coerceAtLeast(0.0)
  }

  companion object {
    /** Bestia days in one quarter of a year: 120 / 4. Not the real-world figure. */
    const val DAYS_PER_QUARTER = 30.0
  }
}

/**
 * The world divided into weather regions, and each region's climatology.
 *
 * ### Not a stage, and not a layer
 *
 * `geo/Plates.kt` is the shape this copies: Poisson seed points, a [PointIndex] over them, a record per seed,
 * and **nothing rasterised**. Only `PLATE_ID` is a raster there, and only because the tectonic loop needs a
 * plate per cell; nothing needs a weather region per cell.
 *
 * A `WEATHER_REGION` int layer would also reintroduce exactly the artefact the irregular partition was chosen
 * to avoid: at the climate grid's four-kilometre cells a categorical boundary is a four-kilometre staircase,
 * and `voxel/SurfaceSampler.biomeAt` exists because a *one*-kilometre one already read as a drawn line.
 * Querying the index at full world-space precision has no staircase at all.
 *
 * The weather itself is not stored for a stronger reason: a stage is `f(seed, region, upstream)` and weather is
 * `f(seed, region, t)`. There is no `t` in the signature and `GenContext` has no clock. Freezing it at `t = 0`
 * would store a meaningless instant; storing a year would be 120 days times four channels times several
 * hundred regions, which is the table the "a pure function of the seed must not get a table" rule refuses.
 *
 * ### Region size is absolute
 *
 * [DEFAULT_SPACING] does **not** go through `WorldConfig.scaleByLength`. Sixteen kilometres is a statement
 * about how far a player walks before the sky should look different, which is a fact about the player rather
 * than about the world's size.
 *
 * Measured: **43 to 49 regions on the 128 km world and 647 to 673 on the 512 km one**, because Bridson packs
 * at about 0.70 points per `r²` rather than the 1.0 that `area / r²` assumes. On the small world only fifteen
 * to twenty-five of them contain any land.
 *
 * ### The partition does not wrap
 *
 * Genesis wraps in both axes and this does not join across the seam, so a region there is split in two. That
 * is the same trade `oceanBorderMetres` already makes and the seam is inside two and a half kilometres of open
 * water. The *field* is a different matter - it is defined on all of ℝ² and has no seam anywhere.
 */
class WeatherRegions private constructor(
  val regions: List<WeatherRegion>,
  private val index: PointIndex
) {

  val count get() = regions.size

  /** Which region covers a world position. */
  fun regionAt(worldX: Double, worldY: Double): WeatherRegion = regions[index.nearest(worldX, worldY)]

  /** How many regions hold any land at all. The number a designer could name. */
  val inhabitedCount get() = regions.count { it.landShare > 0.0 }

  companion object {
    /** Metres between region seed points. Absolute; see the class KDoc. */
    const val DEFAULT_SPACING = 16_000.0

    private const val SEED_SALT = 0x57656174686572L

    /**
     * Divides [generated] into regions and summarises each.
     *
     * One pass over the climate grid with one nearest-neighbour query per cell: a thousand calls on the
     * genesis world and sixteen thousand on the demo one, against the sixteen million `PointIndex` was built
     * to answer for tectonics.
     */
    fun of(generated: GeneratedWorld, spacing: Double = DEFAULT_SPACING): WeatherRegions {
      val config = generated.config
      val layers = generated.world.layers

      val temperature = layers.require<FloatLayer>(LayerId.TEMPERATURE)
      val range = layers.require<FloatLayer>(LayerId.TEMPERATURE_RANGE)
      val toOcean = layers.require<FloatLayer>(LayerId.DISTANCE_TO_OCEAN)
      val seasons = SeasonalPrecipitation.LAYERS.map { layers.require<FloatLayer>(it) }

      // Optional: a partial pipeline or an older world has no mana, and "no mana anywhere" is the right
      // answer there rather than a failure.
      val mana = layers[LayerId.MANA_DENSITY] as? FloatLayer

      // Elevation and biome are on the *base* grid, not the climate grid, so they are sampled by world
      // position rather than indexed - the mistake that had the economy stage reading polar temperature at a
      // grid corner for every catchment in the world.
      val elevation = layers.require<FloatLayer>(LayerId.ELEVATION)
      val biome = layers.require<IntLayer>(LayerId.BIOME)

      val bounds = Aabb(0.0, 0.0, config.widthMetres, config.heightMetres)
      val rng = GenRng(GenRng.hash(config.seed, SEED_SALT))
      val seeds = PoissonDisk.sample(bounds, spacing, rng)
      require(seeds.isNotEmpty()) { "no weather region seeds fitted in $bounds at $spacing m" }

      val index = PointIndex(seeds, bounds.expanded(spacing))

      val accumulators = List(seeds.size) { Accumulator() }
      val climateRegion = temperature.region
      val metres = climateRegion.resolution.metresPerCell

      for (y in 0 until climateRegion.height) {
        for (x in 0 until climateRegion.width) {
          val worldX = (climateRegion.minX + x + 0.5) * metres
          val worldY = (climateRegion.minY + y + 0.5) * metres
          val into = accumulators[index.nearest(worldX, worldY)]

          val cellX = climateRegion.minX + x
          val cellY = climateRegion.minY + y

          into.cells++
          into.temperature += temperature[cellX, cellY]
          into.range += range[cellX, cellY]
          into.toOcean += toOcean[cellX, cellY]
          for (season in seasons.indices) {
            into.seasonal[season] = into.seasonal[season] + seasons[season][cellX, cellY]
          }

          val ground = elevation.sampleBilinear(worldX, worldY)
          into.elevations.add(ground)
          if (ground > config.seaLevel) into.land++

          val here = Biome.entries[biome.sampleNearest(worldX, worldY)]
          if (here in LOOSE_SURFACE) into.loose++

          if (mana != null) {
            val value = mana.sampleBilinear(worldX, worldY)
            into.mana += value
            into.manaValues.add(value)
          }
        }
      }

      val maritimeRange = config.scaleByLength(ClimateParams().maritimeRange)

      val regions = seeds.mapIndexed { i, centre ->
        val acc = accumulators[i]
        val cells = acc.cells.coerceAtLeast(1)
        acc.elevations.sort()
        acc.manaValues.sort()

        WeatherRegion(
          index = i,
          centre = centre,
          cellCount = acc.cells,
          seasonalPrecipitation = DoubleArray(seasons.size) { acc.seasonal[it] / cells },
          meanTemperature = acc.temperature / cells,
          temperatureRange = acc.range / cells,
          // Through the same `1 - exp(-d / maritimeRange)` form and the same *scaled* range ClimateStage
          // used, or this disagrees with the TEMPERATURE_RANGE it sits beside.
          continentality = 1.0 - Math.exp(-(acc.toOcean / cells) / maritimeRange),
          meanElevation = acc.elevations.average(),
          relief = percentile(acc.elevations, 0.95) - percentile(acc.elevations, 0.05),
          landShare = acc.land.toDouble() / cells,
          looseSurfaceShare = acc.loose.toDouble() / cells,
          meanMana = if (mana == null) 0.0 else acc.mana / cells,
          peakMana = if (mana == null) 0.0 else percentile(acc.manaValues, 0.95),
          latitude = ClimateStage.latitudeOf(centre.y / config.heightMetres)
        )
      }

      return WeatherRegions(regions, index)
    }

    /** Biomes a wind can pick material up off. What a sandstorm needs. */
    private val LOOSE_SURFACE = setOf(
      Biome.DESERT, Biome.BEACH, Biome.BADLANDS, Biome.COLD_DESERT, Biome.DRYLAND
    )

    private fun percentile(sorted: MutableList<Double>, share: Double): Double {
      if (sorted.isEmpty()) return 0.0
      val at = (share * (sorted.size - 1)).toInt().coerceIn(0, sorted.size - 1)
      return sorted[at]
    }

    private class Accumulator {
      var cells = 0
      var temperature = 0.0
      var range = 0.0
      var toOcean = 0.0
      var land = 0
      var loose = 0
      var mana = 0.0
      val seasonal = DoubleArray(SeasonalPrecipitation.COUNT)
      val elevations = ArrayList<Double>()
      val manaValues = ArrayList<Double>()
    }
  }
}
