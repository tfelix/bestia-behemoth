package net.bestia.worldgen.bio

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.DistanceTransform
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.hydro.HydrologyStage
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** Tuning for [BiomeStage]. */
data class BiomeParams(

  /** Distance from a channel within which a dry region gets a riparian strip, in metres. */
  val riparianRange: Double = 1_600.0,

  /** Discharge in cubic metres per second above which a cell counts as a river for riparian purposes. */
  val riparianDischarge: Double = 1.5,

  /** Distance from the sea within which a low, flat cell becomes beach, in metres. */
  val beachRange: Double = 1_400.0,

  /** Slope below which flat, wet ground becomes wetland rather than forest or grass. */
  val wetlandSlope: Double = 0.004,

  /**
   * Slope above which soft rock becomes badlands, and above which any rock reads as cliff.
   *
   * Both are **resolution dependent** and both were originally set from voxel-scale intuition, which is
   * wrong by a wide margin. Averaged over a kilometre cell, a 0.3 slope is three hundred metres of drop
   * per kilometre - dramatic mountain terrain - and setting the cliff threshold there classifies every
   * continental margin and mountain front in the world as cliff, which swamps the climatic biomes under a
   * grey band. These are for kilometre cells; a chunk-scale classifier would want several times more.
   */
  val badlandsSlope: Double = 0.22,

  val cliffSlope: Double = 0.45,

  /** Mean annual temperature below which permanent ice forms given enough precipitation. */
  val glacierTemperature: Double = -7.0,

  /** Maximum soil depth in metres. Deep alluvium, not bedrock at the surface. */
  val maxSoilDepth: Double = 9.0
)

/**
 * Stage 5: biomes, soil fertility and soil depth.
 *
 * Two kinds of biome come out of here, and the distinction is the interesting part.
 *
 * **Climatic** biomes come from [Biomes.classify] - a weighted distance to a set of prototypes in
 * climate-and-terrain space.
 *
 * **Edge** biomes come from adjacency to something rather than from the weather: a riparian strip beside
 * a river, a beach inside the coastline, wetland on flat wet ground, badlands and cliff on steep soft
 * and steep hard rock. These are the ones players actually navigate by. A mesic green ribbon through a
 * desert tells you where the water is from a kilometre away, and no climate classifier will ever produce
 * it, because the climate either side of the river is identical.
 *
 * Soil fertility matters beyond vegetation: it is the largest single term in settlement placement two
 * stages from now, because civilisations grow where food grows.
 */
class BiomeStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: BiomeParams = BiomeParams()
) : Stage {

  override val id = ID

  // 2: the runner-up biome is kept as BIOME_SECONDARY instead of being scored and discarded.
  override val version = 2
  override val dependencies =
    listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.BIOME),
    StageOutput.Raster(LayerId.BIOME_SECONDARY),
    StageOutput.Raster(LayerId.BIOME_CONFIDENCE),
    StageOutput.Raster(LayerId.SOIL_FERTILITY),
    StageOutput.Raster(LayerId.SOIL_DEPTH)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val sediment = Grid.from(ctx.layers.float(LayerId.SEDIMENT))
    val hardness = Grid.from(ctx.layers.float(LayerId.ROCK_HARDNESS))
    val discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE))
    val accumulation = Grid.from(ctx.layers.float(LayerId.FLOW_ACCUMULATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val lakeId = ctx.layers.int(LayerId.LAKE_ID)

    val temperature = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE), region)
    val temperatureRange = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE_RANGE), region)
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)
    val seasonality = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION_SEASONALITY), region)

    // Both buffers the edge biomes need. Computed on this grid rather than read from climate, because a
    // 4 km distance-to-ocean cannot place a 1 km coastal strip.
    val toOcean = DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
      val i = elevation.index(x, y)
      !waterLevel.data[i].isNaN() && lakeId[x + region.minX, y + region.minY] == 0
    }
    val toChannel = DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
      discharge.data[elevation.index(x, y)] >= params.riparianDischarge
    }

    val biome = IntGrid(region.width, region.height)
    val secondary = IntGrid(region.width, region.height)
    val confidence = Grid(region.width, region.height)
    val fertility = Grid(region.width, region.height)
    val soilDepth = Grid(region.width, region.height)

    val maxAccumulation = ln(1.0 + accumulation.max())

    // Classification is per cell and reads nothing this loop writes, so the rows split cleanly. The one
    // thing that cannot be shared is the axis buffer `Biomes.describe` fills: it was hoisted out of the
    // loop to keep the classifier allocation-free, and a single one across bands would have every worker
    // overwriting the sample the others are about to classify. One per band keeps both properties.
    Parallel.rows(region.height, region.width) { yFrom, yUntil ->
    val sample = DoubleArray(BiomeAxis.COUNT)

    for (y in yFrom until yUntil) {
      for (x in 0 until region.width) {
        val i = elevation.index(x, y)
        val z = elevation.data[i]
        val slope = elevation.gradient(x, y, metres)
        val above = max(0.0, z - seaLevel)

        val wetness = wetnessAt(precipitation.data[i], slope, accumulation.data[i], maxAccumulation)

        Biomes.describe(
          out = sample,
          temperature = temperature.data[i],
          precipitation = precipitation.data[i],
          seasonality = seasonality.data[i],
          temperatureRange = temperatureRange.data[i],
          elevationAboveSea = above,
          slope = slope,
          wetness = wetness
        )
        val match = Biomes.classify(sample)

        val chosen = override(
          climatic = match.biome,
          hasWater = !waterLevel.data[i].isNaN(),
          isLake = lakeId[x + region.minX, y + region.minY] != 0,
          temperature = temperature.data[i],
          precipitation = precipitation.data[i],
          elevationAboveSea = above,
          slope = slope,
          hardness = hardness.data[i],
          wetness = wetness,
          distanceToOcean = toOcean.data[i],
          distanceToChannel = toChannel.data[i]
        )

        biome.data[i] = chosen.ordinal
        // An overridden cell is not a classification at all, so reporting the classifier's confidence
        // for it would be a lie. Edge biomes are certain by construction.
        val overridden = chosen != match.biome
        confidence.data[i] = if (overridden) 1.0 else match.confidence

        // **And an overridden cell gets the sentinel rather than the climatic winner it displaced.** The
        // climatic answer is real information - "if this were not a beach it would be temperate forest" - and
        // it is tempting to keep it here for free. It is the wrong slot for it: this layer means "the biome
        // that came second in the classification", and there was no classification. Storing the displaced
        // winner would make a beach read as a beach/forest transition, so anything measuring how much of the
        // world is ecotone would count every shoreline and every cliff in the total.
        //
        // Nothing is lost by the choice, because the paired confidence is 1.0 on exactly these cells and a
        // blend weight of zero makes the identity unused either way. The sentinel is the honest of the two
        // encodings of the same behaviour.
        secondary.data[i] = when {
          overridden -> LayerId.NO_SECONDARY
          else -> match.runnerUp?.ordinal ?: LayerId.NO_SECONDARY
        }

        val weathering = weatheringAt(hardness.data[i], temperature.data[i], precipitation.data[i])
        fertility.data[i] = fertilityAt(chosen, sediment.data[i], weathering, slope, wetness)
        soilDepth.data[i] = soilDepthAt(chosen, sediment.data[i], weathering, slope)
      }
    }
    }

    return StageResult.of(
      biome.toLayer(LayerId.BIOME, region),
      secondary.toLayer(LayerId.BIOME_SECONDARY, region),
      confidence.toLayer(LayerId.BIOME_CONFIDENCE, region),
      fertility.toLayer(LayerId.SOIL_FERTILITY, region),
      soilDepth.toLayer(LayerId.SOIL_DEPTH, region)
    )
  }

  /**
   * The edge biomes, in priority order.
   *
   * Order is the whole content of this function. Water beats everything; ice beats terrain; a cliff is a
   * cliff whatever grows on the plateau above it; a beach beats the biome inland of it; and a riparian
   * strip only replaces a *dry* biome, because a green ribbon through a rainforest would be invisible
   * and a green ribbon through a desert is the point.
   */
  private fun override(
    climatic: Biome,
    hasWater: Boolean,
    isLake: Boolean,
    temperature: Double,
    precipitation: Double,
    elevationAboveSea: Double,
    slope: Double,
    hardness: Double,
    wetness: Double,
    distanceToOcean: Double,
    distanceToChannel: Double
  ): Biome = when {
    hasWater && isLake -> Biome.LAKE
    hasWater -> Biome.OCEAN

    // Permanent ice needs cold *and* snowfall. Cold and dry is a polar desert, which is a real and
    // visually distinct place - most of Antarctica's interior, and it is not glaciated.
    temperature < params.glacierTemperature && precipitation > GLACIER_PRECIPITATION ->
      if (elevationAboveSea > GLACIER_ELEVATION) Biome.GLACIER else Biome.ICE_SHEET

    slope >= params.cliffSlope -> Biome.CLIFF
    slope >= params.badlandsSlope && hardness < BADLANDS_HARDNESS && precipitation < BADLANDS_RAIN ->
      Biome.BADLANDS

    // A beach is a metres-wide feature being asked for on a kilometre grid, so what this really marks is
    // "the cell the shoreline runs through". The architecture document wants it derived from a coastline
    // polyline instead, which would place it properly; that needs a coastline in the vector tier.
    distanceToOcean <= params.beachRange && elevationAboveSea < BEACH_ELEVATION &&
        slope < BEACH_SLOPE && !climatic.isWater -> Biome.BEACH

    slope < params.wetlandSlope && wetness > WETLAND_WETNESS -> Biome.WETLAND

    distanceToChannel <= params.riparianRange && climatic in DRY_BIOMES -> Biome.RIPARIAN

    else -> climatic
  }

  /**
   * Soil wetness: how much water is actually available to a plant.
   *
   * Not the same thing as rainfall. A steep slope sheds what falls on it, and a valley floor collects
   * what fell on everything above it - which is why a valley in a dry region is green and the ridge
   * beside it is not.
   */
  private fun wetnessAt(
    precipitation: Double,
    slope: Double,
    accumulation: Double,
    maxAccumulation: Double
  ): Double {
    val rain = (precipitation / 2200.0).coerceIn(0.0, 1.0)
    val shed = 1.0 - (slope / 0.25).coerceIn(0.0, 1.0)
    val collected = if (maxAccumulation <= 0.0) 0.0 else ln(1.0 + accumulation) / maxAccumulation

    return (0.5 * rain + 0.28 * shed + 0.22 * collected).coerceIn(0.0, 1.0)
  }

  /** How fast rock turns into soil: soft rock, warm and wet, weathers fastest. */
  private fun weatheringAt(hardness: Double, temperature: Double, precipitation: Double): Double {
    val softness = (1.0 - hardness).coerceIn(0.0, 1.0)
    val warmth = ((temperature + 5.0) / 35.0).coerceIn(0.0, 1.0)
    val wet = (precipitation / 2000.0).coerceIn(0.0, 1.0)
    return (softness * 0.5 + warmth * 0.25 + wet * 0.25).coerceIn(0.0, 1.0)
  }

  /**
   * Soil fertility, the term that decides where civilisations end up.
   *
   * Alluvium first - the reason every early civilisation is on a floodplain - then weathering, then what
   * the vegetation puts back, minus what a slope loses. Water is deliberately worthless: an ocean cell
   * has no soil, and letting it score highly would put farms in the sea two stages from now.
   */
  private fun fertilityAt(
    biome: Biome,
    sediment: Double,
    weathering: Double,
    slope: Double,
    wetness: Double
  ): Double {
    if (biome.isWater) return 0.0

    val alluvium = min(1.0, sediment / 6.0)
    val steepness = (slope / 0.22).coerceIn(0.0, 1.0)

    return (0.10 +
        0.32 * alluvium +
        0.20 * weathering +
        0.20 * biome.litter +
        0.14 * wetness -
        0.38 * steepness).coerceIn(0.0, 1.0)
  }

  /** Soil is thin where it is steep and thick where sediment collected. */
  private fun soilDepthAt(biome: Biome, sediment: Double, weathering: Double, slope: Double): Double {
    if (biome.isWater) return 0.0
    if (biome == Biome.CLIFF) return 0.0

    val flatness = 1.0 - (slope / 0.25).coerceIn(0.0, 1.0)
    val residual = (0.15 + 2.1 * flatness) * (0.4 + 0.6 * weathering)

    return min(params.maxSoilDepth, residual + min(6.0, sediment * 0.55))
  }

  companion object {
    val ID = StageId("biomes")

    /** Biomes dry enough that a riparian strip through them is worth drawing. */
    private val DRY_BIOMES = setOf(
      Biome.DESERT, Biome.COLD_DESERT, Biome.STEPPE, Biome.SHRUBLAND, Biome.SAVANNA, Biome.GRASSLAND
    )

    private const val GLACIER_PRECIPITATION = 150.0
    private const val GLACIER_ELEVATION = 900.0

    private const val BADLANDS_HARDNESS = 0.35
    private const val BADLANDS_RAIN = 700.0

    private const val BEACH_ELEVATION = 22.0
    private const val BEACH_SLOPE = 0.05

    private const val WETLAND_WETNESS = 0.62
  }
}
