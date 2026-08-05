package net.bestia.worldgen.bio

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
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
import net.bestia.worldgen.geo.VolcanismStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
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

  /**
   * Slope below which flat, wet ground becomes a bog or a swamp rather than forest or grass.
   *
   * **Resolution dependent, and it was wrong in exactly the way [badlandsSlope] documents.** At 0.004 this
   * asked a kilometre cell to drop less than four metres across its whole width, which is the flatness of a
   * lake bed rather than of a floodplain - and ANDed with [WETLAND_WETNESS] the intersection all but vanished:
   * the reference world came out **0.02% wetland**, 220 cells out of 935,000, which is indistinguishable from
   * the biome not existing. The note one field below already says that voxel-scale slope intuition is wrong by
   * a wide margin at a kilometre per cell; this is the same mistake, found later.
   *
   * 0.02 is twenty metres of drop per kilometre, which is still unambiguously flat ground - a basin floor or a
   * valley bottom - and leaves the wetness gate as the binding constraint, which is the right way round: what
   * makes a wetland is the water, and the slope is only there to keep it off hillsides.
   */
  val wetlandSlope: Double = 0.02,

  /**
   * Slope above which soft rock becomes badlands.
   *
   * **Resolution dependent**, and originally set from voxel-scale intuition, which is wrong by a wide
   * margin. Averaged over a kilometre cell, a 0.3 slope is three hundred metres of drop per kilometre -
   * dramatic mountain terrain. This is for kilometre cells; a chunk-scale classifier would want several
   * times more.
   */
  val badlandsSlope: Double = 0.22,

  /**
   * Slope above which a kilometre cell carries no soil at all.
   *
   * Only [BiomeStage.soilDepthAt] reads this, and through it `LayerId.SOIL_DEPTH`, which is what stops
   * vegetation growing on a mountain front. It used to be `cliffSlope` and used to assign `Biome.CLIFF`;
   * that biome is gone (see [Biome]) and the threshold stayed, because "steep enough that soil washes off"
   * is a real and useful thing to say at kilometre scale.
   *
   * It is **not** the threshold that decides whether a player sees bare rock. That is a voxel-scale
   * gradient over the materialised surface - `ChunkMaterializer.BARE_ROCK_GRADIENT` - because a kilometre
   * average cannot resolve a cliff face, and drawing the two from one number would mean either soil on
   * crags or bare rock across whole mountain ranges.
   */
  val bareRockSlope: Double = 0.45,

  /** Mean annual temperature below which permanent ice forms given enough precipitation. */
  val glacierTemperature: Double = -7.0,

  /**
   * How far from a vent an edifice reaches, in metres.
   *
   * **Raw, not `scaleByLength`d**, and that is `ManaStage`'s wavelength argument verbatim: a volcano is 10-30 km
   * across on Earth and should be 10-30 km across on a 128 km world and on a 512 km one alike. Scaling it would
   * make a genesis-scale world's volcanoes four times wider than its mountains are tall.
   *
   * This, and not a threshold on `LayerId.VOLCANISM`, is what places the volcanic biomes. The raster is a
   * *regional* field with a 40 km reach, so thresholding it selects a continuous band either side of every
   * subduction zone in the world - hundreds of kilometres of it - which is the opposite of rare. A vent is a
   * point, and a disc a few kilometres across around a point is an edifice.
   *
   * ### The share it produces depends on world size, and that is a property of the decision above
   *
   * `arcVentSpacing` *is* scaled by world length, so a world of any size gets roughly the same **number** of
   * vents - twenty-four on every size measured - while its land area goes as the square of its edge. A fixed 5 km
   * edifice therefore covers a share that falls with world size. Measured land share for the two biomes together,
   * with `checkVolcanicBiomesStandOnVolcanoes` clean at every size:
   *
   * ```
   *   128 cells   ~9.2%      192 cells   ~5.6%      256 cells   ~2.4%      512 cells (genesis)   ~1.2%
   * ```
   *
   * It is not a clean inverse square, and the reason is worth knowing before retuning: at 128 cells two dozen
   * 5 km discs *overlap*, so the share saturates well below the sum of their areas, while at 512 they barely
   * touch and 1.2% is close to the un-overlapped ideal.
   *
   * Tuned for the size that ships, which is `StandardWorld.demoConfig`'s 512. One land cell in eighty is the rare
   * province this is meant to be. The small worlds the tests use are genuinely mostly-volcano and that is
   * arithmetic rather than a bug - two dozen volcanoes on a 128 km continent *is* a volcanic continent - so
   * `Invariants` bounds the share against the vents the world actually has rather than against a flat number.
   */
  val volcanicVentRange: Double = 5_000.0,

  /**
   * Share of [volcanicVentRange] within which the ground is a bare volcanic field.
   *
   * Measured from the vent outward, so this is the cone and its flow aprons. Tested *before*
   * [geothermalHeat] and therefore wins where they overlap, which is why the two are ordered in `init`.
   *
   * **0.55, chosen from the area ratio rather than from the radius.** The basin is the annulus outside this, so
   * the two areas go as `f²` against `(1 - f²) × the share the wetness gate passes`; at 0.38 the annulus was
   * six times the disc and the basin came out 3.7 times the field, which is not the picture this pair is for -
   * the field is meant to be the volcano and the basin its valleys, not a thin cap inside a broad ring. At 0.55
   * they land about one to one and a half. Measured on the 256-cell reference world.
   */
  val volcanicFieldHeat: Double = 0.55,

  /** Share of [volcanicVentRange] within which a *wet* cell becomes a geothermal basin. */
  val geothermalHeat: Double = 1.0,

  /**
   * Wetness a cell needs before it can be a geothermal basin rather than merely near a volcano.
   *
   * Two jobs, and both matter. **A geyser needs groundwater** - a dry hillside near a vent is a dry hillside -
   * and [wetnessAt] is high on valley floors, so the basin lands in the *valleys* around a cone while the field
   * caps the cone itself. That is what stops the pair reading as a concentric bullseye on a kilometre raster.
   */
  val geothermalWetness: Double = 0.42,

  /** Maximum soil depth in metres. Deep alluvium, not bedrock at the surface. */
  val maxSoilDepth: Double = 9.0
) : Params {

  init {
    require(riparianRange >= 0.0) { "riparianRange must not be negative, was $riparianRange" }
    require(riparianDischarge >= 0.0) { "riparianDischarge must not be negative, was $riparianDischarge" }
    require(beachRange >= 0.0) { "beachRange must not be negative, was $beachRange" }
    require(wetlandSlope >= 0.0) { "wetlandSlope must not be negative, was $wetlandSlope" }
    // Ordered because the two describe the same axis at different severities: badlands is soft rock steep
    // enough to erode into gullies, bare rock is steep enough to hold no soil at all. Inverting them would
    // put badlands - a biome whose whole character is deep, soft, weathered ground - only on slopes already
    // declared to have no soil on them.
    require(badlandsSlope in 0.0..bareRockSlope) {
      "badlandsSlope $badlandsSlope must be in [0, bareRockSlope $bareRockSlope]"
    }
    require(glacierTemperature.isFinite()) { "glacierTemperature must be finite, was $glacierTemperature" }
    require(volcanicVentRange >= 0.0) { "volcanicVentRange must not be negative, was $volcanicVentRange" }
    require(volcanicFieldHeat in 0.0..1.0) { "volcanicFieldHeat must be a share, was $volcanicFieldHeat" }
    require(geothermalHeat in 0.0..1.0) { "geothermalHeat must be a share, was $geothermalHeat" }
    // Ordered, and the badlands pair's reason applies with more force: the field is tested first and wins, so an
    // inverted pair would make GEOTHERMAL_BASIN a biome that silently never appears in any world. A biome that
    // is never assigned looks identical to one that is merely rare, which is the failure that gets shipped.
    require(volcanicFieldHeat <= geothermalHeat) {
      "volcanicFieldHeat $volcanicFieldHeat must be inside geothermalHeat $geothermalHeat, or the basin is dead"
    }
    require(geothermalWetness in 0.0..1.0) { "geothermalWetness must be a share, was $geothermalWetness" }
    require(maxSoilDepth >= 0.0) { "maxSoilDepth must not be negative, was $maxSoilDepth" }
  }

  override fun digest() = ParamsDigest()
    .put("riparianRange", riparianRange)
    .put("riparianDischarge", riparianDischarge)
    .put("beachRange", beachRange)
    .put("wetlandSlope", wetlandSlope)
    .put("badlandsSlope", badlandsSlope)
    .put("bareRockSlope", bareRockSlope)
    .put("glacierTemperature", glacierTemperature)
    .put("volcanicVentRange", volcanicVentRange)
    .put("volcanicFieldHeat", volcanicFieldHeat)
    .put("geothermalHeat", geothermalHeat)
    .put("geothermalWetness", geothermalWetness)
    .put("maxSoilDepth", maxSoilDepth)
}

/**
 * Stage 5: biomes, soil fertility and soil depth.
 *
 * Two kinds of biome come out of here, and the distinction is the interesting part.
 *
 * **Climatic** biomes come from [Biomes.classify] - a weighted distance to a set of prototypes in
 * climate-and-terrain space.
 *
 * **Edge** biomes come from adjacency to something rather than from the weather: a riparian strip beside
 * a river, a beach inside the coastline, wetland on flat wet ground, badlands on steep soft
 * rock. These are the ones players actually navigate by. A mesic green ribbon through a
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

  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, Biomes.catalogueDigest())
  override val dependencies =
    listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, VolcanismStage.ID)
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

    // A third transform beside those two, and for the same reason they are here rather than read from a layer:
    // the answer wanted is a distance in metres on *this* grid, and there is no raster of it. Deliberately not a
    // new `LayerId` - the only reader is this loop, and `BIOME_SECONDARY`'s KDoc refuses a raster that is a
    // function of features already on disk.
    val vents = ventCells(ctx, region)
    val toVent = DistanceTransform.euclideanMetres(region.width, region.height, metres) { x, y ->
      vents[y * region.width + x]
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
          distanceToChannel = toChannel.data[i],
          distanceToVent = toVent.data[i]
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

    rankConfidence(confidence, secondary)

    return StageResult.of(
      biome.toLayer(LayerId.BIOME, region),
      secondary.toLayer(LayerId.BIOME_SECONDARY, region),
      confidence.toLayer(LayerId.BIOME_CONFIDENCE, region),
      fertility.toLayer(LayerId.SOIL_FERTILITY, region),
      soilDepth.toLayer(LayerId.SOIL_DEPTH, region)
    )
  }

  /**
   * Rewrites the raw classifier score as a **percentile rank over this world's own distribution**.
   *
   * ### Why the raw score cannot be a blend weight
   *
   * `1 - sqrt(best/second)` is a fine *ranking* of how transitional a cell is and a bad *fraction*, because
   * a dozen prototypes in seven dimensions put the nearest and second-nearest distances close together
   * almost everywhere. Measured on the reference world with `probe --ecotone`, over the 41.7% of cells that
   * have a runner-up at all: **median 0.069, p75 0.133, p95 0.361.** A number whose 95th percentile is 0.36 is
   * not a share of anything.
   *
   * `SurfaceSampler.biomeAt` had been compensating with a cutoff, and the same measurement shows why that was
   * the wrong shape of fix rather than merely an ugly one. At a cutoff of 0.2 the raw distribution puts *87% of
   * runner-up cells* below it, so it excluded almost nothing and mostly rescaled - handing the median cell 14%
   * of its ground to the runner-up. The mixing was strongest exactly where it should have been weakest: in the
   * ordinary middle of the distribution.
   *
   * A rank is uniform by construction, so it is a share by construction. The median cell now reads about 8%
   * runner-up and a near-tied cell keeps its half, which is the ramp the effect always wanted. The compensating
   * cutoff is gone, and the world-wide ecotone area barely moved - see `SUMMARY.md` for the before and after.
   *
   * ### What it costs
   *
   * **A whole-raster pass**: a percentile needs the distribution, so this cannot be computed per cell and it is
   * why the ranking happens here rather than in `Biomes.classify`. It also means the value is only meaningful
   * for a raster covering a whole world - were this stage ever tiled, each tile would rank against itself and
   * two tiles would disagree about the same cell. It is a `StageScale.WORLD` stage and that is not a hazard
   * today, but it is the assumption to check first if that ever changes.
   *
   * Ties share a rank (the count of values strictly below), so the result does not depend on iteration order
   * and a dead tie - two prototypes with identical scores - lands at exactly 0.
   */
  private fun rankConfidence(confidence: Grid, secondary: IntGrid) {
    val ranked = ArrayList<Int>()
    for (i in confidence.data.indices) {
      if (secondary.data[i] != LayerId.NO_SECONDARY) ranked.add(i)
    }

    // One cell cannot be ranked against anything, and the cells with no runner-up keep the 1.0 the loop above
    // wrote - `BiomeBlendTest` asserts that pairing, because a sentinel with a low confidence would be a cell
    // asking to be blended with nothing.
    if (ranked.size <= 1) {
      for (i in ranked) confidence.data[i] = 1.0
      return
    }

    val sorted = DoubleArray(ranked.size) { confidence.data[ranked[it]] }
    sorted.sort()

    val last = (ranked.size - 1).toDouble()
    for (i in ranked) {
      confidence.data[i] = countBelow(sorted, confidence.data[i]) / last
    }
  }

  /** How many entries of the sorted array are strictly less than [value]: the lower bound, by bisection. */
  private fun countBelow(sorted: DoubleArray, value: Double): Int {
    var low = 0
    var high = sorted.size
    while (low < high) {
      val mid = (low + high) ushr 1
      if (sorted[mid] < value) low = mid + 1 else high = mid
    }
    return low
  }

  /**
   * The edge biomes, in priority order.
   *
   * Order is the whole content of this function. Water beats everything; ice beats terrain; a beach beats
   * the biome inland of it; and a riparian strip only replaces a *dry* biome, because a green ribbon through
   * a rainforest would be invisible and a green ribbon through a desert is the point.
   *
   * Steepness is conspicuously absent, and used to be the third rung. Slope is not a biome - it says nothing
   * about what grows or what lives there - and a rung for it erased the biome underneath, so an ice cliff
   * and a desert scarp classified identically. It reaches the ground through `SOIL_DEPTH` and through the
   * voxel tier's own gradient instead. See [Biome].
   *
   * ### The volcanic pair sits above ice, not where `CLIFF` used to be
   *
   * Below the ice rung most of them would be lost, and the arithmetic says so: a hotspot cone stands at up to
   * 3,800 m, which by lapse rate is some 24 °C colder than the ground around it, so the ice rung claims exactly
   * the summits a volcanic field wants. Above it is also the truthful order - a flow field still cooling holds no
   * snowpack, and that is *why* a young volcano is visible from the air inside an ice cap.
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
    distanceToChannel: Double,
    distanceToVent: Double
  ): Biome = when {
    hasWater && isLake -> Biome.LAKE
    hasWater -> Biome.OCEAN

    // The cone and its aprons. Above the ice rung; see the KDoc.
    distanceToVent <= params.volcanicVentRange * params.volcanicFieldHeat -> Biome.VOLCANIC_FIELD

    // And the valleys around it, where there is groundwater for a vent to boil. The wetness gate is what makes
    // this the valley floors rather than an annulus around the cone.
    distanceToVent <= params.volcanicVentRange * params.geothermalHeat &&
        wetness >= params.geothermalWetness -> Biome.GEOTHERMAL_BASIN

    // Permanent ice needs cold *and* snowfall. Cold and dry is a polar desert, which is a real and
    // visually distinct place - most of Antarctica's interior, and it is not glaciated.
    temperature < params.glacierTemperature && precipitation > GLACIER_PRECIPITATION -> Biome.ICE_SHEET

    slope >= params.badlandsSlope && hardness < BADLANDS_HARDNESS && precipitation < BADLANDS_RAIN ->
      Biome.BADLANDS

    // A beach is a metres-wide feature being asked for on a kilometre grid, so what this really marks is
    // "the cell the shoreline runs through". The architecture document wants it derived from a coastline
    // polyline instead, which would place it properly; that needs a coastline in the vector tier.
    distanceToOcean <= params.beachRange && elevationAboveSea < BEACH_ELEVATION &&
        slope < BEACH_SLOPE && !climatic.isWater -> Biome.BEACH

    // Flat, wet ground, split on temperature into the two wetlands that are actually different places. The
    // temperature is already a parameter of this function for the ice rung, so the split costs one comparison
    // and no new input - which is most of why it belongs on this rung rather than in a prototype. See [Biome.BOG].
    slope < params.wetlandSlope && wetness > WETLAND_WETNESS ->
      if (temperature < BOG_TEMPERATURE) Biome.BOG else Biome.SWAMP

    distanceToChannel <= params.riparianRange && climatic in DRY_BIOMES -> Biome.RIPARIAN

    else -> climatic
  }

  /**
   * Which cells of this region hold a volcanic vent, as the seed set for a distance transform.
   *
   * Marked by the cell the vent's position falls in rather than by a radius, because the transform is what turns
   * that into a distance - and a vent one metre outside this region still seeds the cell it is in on the
   * neighbouring one, so nothing is lost at a boundary that a `StageScale.WORLD` stage would ever see.
   */
  private fun ventCells(ctx: GenContext, region: CellRegion): BooleanArray {
    val metres = region.resolution.metresPerCell
    val marked = BooleanArray(region.width * region.height)

    for (feature in ctx.features.query(region.toWorld())) {
      if (feature.kind != FeatureKind.VOLCANIC_VENT) continue
      val vent = feature as? PointMarker ?: continue

      val x = (vent.position.x / metres).toInt() - region.minX
      val y = (vent.position.y / metres).toInt() - region.minY
      if (x < 0 || y < 0 || x >= region.width || y >= region.height) continue

      marked[y * region.width + x] = true
    }

    return marked
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
    // Fresh basalt is not soil, however long it has been cooling by the standards of this simulation. Without
    // this the formula's own floor plus a weathering term gives a cooling flow field about 0.4 - respectable
    // farmland - and `SettlementStage` puts a town on the volcano. GEOTHERMAL_BASIN is deliberately *not* here:
    // a warm valley beside a vent is good ground, which is the whole of why Reykjavik is where it is.
    if (biome == Biome.VOLCANIC_FIELD) return 0.0

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
    // Bare rock, for [fertilityAt]'s reason. This is also what stops a tree standing on a lava field: the
    // vegetation scatter's density reads `SOIL_DEPTH`.
    if (biome == Biome.VOLCANIC_FIELD) return 0.0
    // Steep enough that nothing stays on it. Read off the slope rather than off a `CLIFF` biome, which is
    // what this used to test - so the cell keeps the biome its climate gives it and still carries no soil.
    if (slope >= params.bareRockSlope) return 0.0

    val flatness = 1.0 - (slope / 0.25).coerceIn(0.0, 1.0)
    val residual = (0.15 + 2.1 * flatness) * (0.4 + 0.6 * weathering)

    return min(params.maxSoilDepth, residual + min(6.0, sediment * 0.55))
  }

  companion object {
    val ID = StageId("biomes")

    /** Biomes dry enough that a riparian strip through them is worth drawing. */
    private val DRY_BIOMES = setOf(
      Biome.DESERT, Biome.COLD_DESERT, Biome.DRYLAND, Biome.GRASSLAND
    )

    /**
     * Millimetres of annual precipitation permanent ice needs, on top of the cold.
     *
     * Internal rather than private so `VolcanicBiomeTest` can state the ice rung's own condition and assert that
     * volcanic ground beats it. That is the one assertion which proves the override ladder's ordering has an
     * effect, and it cannot be written without both halves of the condition the ladder is being compared against.
     */
    internal const val GLACIER_PRECIPITATION = 150.0

    private const val BADLANDS_HARDNESS = 0.35
    private const val BADLANDS_RAIN = 700.0

    private const val BEACH_ELEVATION = 22.0
    private const val BEACH_SLOPE = 0.05

    private const val WETLAND_WETNESS = 0.62

    /**
     * Mean annual temperature below which a wetland is a bog rather than a swamp, in degrees.
     *
     * Where peat wins: below about this, decay runs slower than production the year round, so dead matter
     * accumulates instead of rotting - which is the whole of what separates the two biomes and why one
     * preserves what falls into it. Above it the ground is a warm swamp, and the intermediate reed fen that
     * a third biome would name is deliberately shared between them; see [Biome.BOG] for why there is no
     * `MARSH`.
     */
    internal const val BOG_TEMPERATURE = 12.0
  }
}
