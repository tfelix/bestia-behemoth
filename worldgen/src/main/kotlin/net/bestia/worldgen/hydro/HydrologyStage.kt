package net.bestia.worldgen.hydro

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.IntGrid
import net.bestia.worldgen.fields.Tables
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Tuning for [HydrologyStage]. */
data class HydrologyParams(

  /** Fraction of precipitation that becomes runoff rather than evaporating or recharging aquifers. */
  val runoffCoefficient: Double = 0.34,

  /**
   * Catchment area in square metres at which a cell starts carrying a channel, at mean rainfall.
   *
   * A *catchment area* rather than a discharge, and that is the fix for small worlds rather than a refactor of
   * one. Channel initiation is scale-free in nature - drainage density is roughly constant, and real channels
   * begin after well under a square kilometre - so expressing the threshold as an area makes it mean the same
   * thing on a world of any size. The equivalent discharge is derived from this and the world's own mean
   * rainfall, which is what keeps the arid-versus-humid distinction that [aridityExponent] exists for.
   *
   * Written as an absolute discharge, as it was, the threshold quietly encoded "and the world is four thousand
   * kilometres across": a 128 km world has catchments a fiftieth of the size, so nothing reached it and the
   * world came out with two rivers. Scaled by [WorldConfig.scaleByArea], so a small world's detail scale brings
   * it down further still.
   *
   * Raised from 93 million when the world became land-dominated. This is the knob for **how many** rivers, and
   * with half a world of land rather than a quarter the same threshold produced a great many of them - and,
   * because a bigger threshold takes longer to reach, produced them short: the network only lit up in the last
   * stretch before the sea, which reads on the map as a comb of little coastal streams rather than as rivers.
   * Fewer, larger catchments is what leaves room for trunks.
   *
   * It is *only* the count. Where a river starts is [channelSlopeExponent]'s question and how far it runs is
   * [aridityExponent]'s, and confusing the three costs a tuning cycle each time: the network keeps its shape
   * across the whole usable range of this number and only thins.
   *
   * **It is not absolute, and it has to be re-measured whenever the climate moves.** The threshold is a
   * *discharge* derived from this area and the world's mean rainfall, so a uniform change in rainfall cancels -
   * but a change in how rainfall is *distributed* does not. Flattening the model's absurd rain shadows took
   * this world from mostly-bone-dry to broadly damp, which raised the [aridityExponent] multiplier on hardly
   * any cells any more, and the same 110 million that had given 106 channels gave 531. Hence 420 million: the
   * count is back to 140 and there are now 52 confluences rather than 14, which is the network being genuinely
   * dendritic rather than a set of separate coastal streams.
   */
  val channelCatchmentArea: Double = 420_000_000.0,

  /**
   * How strongly the channel threshold rises in dry regions.
   *
   * Scaling the threshold by local rainfall rather than using one figure everywhere is what gives an
   * arid region sparse drainage. With a fixed threshold a desert gets the same dense dendritic network
   * as a rainforest, only with less water in it, and the map stops distinguishing them.
   *
   * This is the knob for **how long** a river is, which is not obvious and is why it was the one to move. It is
   * an exponent on a ratio, so at 1.0 an interior receiving a third of the mean rainfall needs three times the
   * catchment before it carries a channel - and on a continental world, where the interior *is* dry, that
   * pushes every channel head down towards the coast and leaves the uplands with no drainage drawn at all.
   * At 0.55 a dry interior still gets a sparser network than a wet coast, which is the whole point of the
   * term, but a river that rises in the mountains is still drawn as rising in the mountains.
   */
  val aridityExponent: Double = 0.55,

  /**
   * How strongly the channel threshold falls on steep ground. Zero restores the area-only threshold.
   *
   * This is the slope-area law for channel initiation - Montgomery and Dietrich's `A * S^n > constant` - and
   * without it the threshold is an area alone, which is wrong in the one way that shows. A hillside sheds its
   * water into a defined channel after a few hectares; a floodplain of the same catchment carries no channel
   * at all, because there is no gradient to cut one. Ignoring that put every channel head on the coastal plain
   * - the only place a purely area-based threshold is ever reached first - and the map came out as combs of
   * short parallel streams running straight off the shore, with the uplands they should have risen in blank.
   *
   * The ratio it is applied to is against **this world's own mean land slope**, not against a constant, for the
   * same reason [channelCatchmentArea] is converted using this world's own mean rainfall: it makes the term a
   * redistribution rather than a discount. A fixed reference slope has to be either above or below a given
   * world's typical ground, and whichever it is, it moves every threshold on the map in that direction and
   * silently becomes a second control on how many rivers there are. Measured: a reference of 0.03 on a world
   * whose land averages nearly three times that took the river count from 93 to 270 while barely moving where
   * the heads sat, which is the wrong axis entirely.
   *
   * The literature puts the exponent near 2 for debris-flow-dominated heads. That is measured at metres, not at
   * kilometre cells where a slope is already an average over a thousand metres of ground, so the spread here
   * would be enormous - hence 1.0 and a hard clamp rather than the textbook figure.
   */
  val channelSlopeExponent: Double = 1.0,

  /**
   * Largest factor the slope term may move the threshold, either way.
   *
   * A clamp rather than a taper because the tails are where this misbehaves: a flat lake bed approaches zero
   * slope and would demand an infinite catchment, and a cliff face would carry a channel from its first cell.
   */
  val channelSlopeRange: Double = 4.0,

  /**
   * Metres of channel that must lie upstream of a cell before it is drawn at all, before world scaling.
   *
   * **The anti-comb knob.** See [RiverNetwork.trimHeadwaters] for the mechanism and the measurement. In
   * short: the herringbone is not a routing artefact and cannot be removed by perturbing the routing or by
   * raising the discharge threshold - it is genuine drainage on genuine ground, drawn at a scale that should
   * not be showing it, and the fix is to stop drawing the fingertips.
   *
   * This is the knob for **how fine** the drawn network is, which is a third axis alongside
   * [channelCatchmentArea]'s how-many and [aridityExponent]'s how-long. Raising it shortens every river a
   * little and deletes the short ones entirely; it never thins a trunk, because a trunk has kilometres of
   * channel above it before the trim reaches anything that matters.
   */
  val minHeadwaterLength: Double = 12_000.0,

  /** Metres of water evaporated from a lake surface per year. Decides which basins are salt lakes. */
  val evaporationDepth: Double = 1.1,

  /** Hydraulic geometry: `width = a * Q^0.5`. */
  val widthCoefficient: Double = 4.2,

  /** Hydraulic geometry: `depth = b * Q^0.4`. */
  val depthCoefficient: Double = 0.36,

  /** Floodplain half-width coefficient: `shoulder = c * Q^0.35`. */
  val shoulderCoefficient: Double = 24.0,

  /**
   * Narrowest and shallowest channel worth cutting, in voxels. See [ChannelGauge] for why these exist.
   *
   * In voxels rather than metres because what they defend against is the grid's resolution, not anything about
   * water: the hydraulic geometry above is correct and still produces channels this pipeline cannot draw.
   */
  val minChannelWidthVoxels: Double = 3.0,
  val minChannelDepthVoxels: Double = 2.0,

  /**
   * Station and vertex spacing along a river centerline, in metres.
   *
   * Also the finest meander the geometry can hold, which is what really sets it: at 1 km cells the
   * *path* carries no information below about 500 m, but the meander is added after smoothing and wants
   * a wavelength of a few hundred metres to look like a river rather than a bent pipe.
   */
  val stationSpacing: Double = 120.0,

  /** Meander amplitude as a multiple of channel width, before slope confinement. */
  val meanderWidthFactor: Double = 2.6,

  /** Ceiling on meander amplitude in metres, so a trunk river cannot wander off its own floodplain. */
  val meanderAmplitudeCap: Double = 420.0,

  /** Meander wavelength as a multiple of channel width. Real rivers sit between 10 and 14. */
  val meanderWavelengthFactor: Double = 11.0,

  /** Reaches shorter than this in metres are dropped: they are single-cell stubs at drainage divides. */
  val minReachLength: Double = 700.0,

  /** Confluence smoothing disc radius, as a multiple of the joined channel's width. */
  val confluenceRadiusFactor: Double = 1.7
) : Params {
  init {
    require(runoffCoefficient in 0.0..1.0) { "runoffCoefficient must be in [0,1]" }
    require(channelCatchmentArea > 0.0) { "channelCatchmentArea must be positive" }
    require(stationSpacing > 0.0) { "stationSpacing must be positive" }
    require(minChannelWidthVoxels >= 0.0 && minChannelDepthVoxels >= 0.0) {
      "channel gauge floors must not be negative"
    }
    require(aridityExponent.isFinite()) { "aridityExponent must be finite, was $aridityExponent" }
    require(channelSlopeExponent.isFinite()) {
      "channelSlopeExponent must be finite, was $channelSlopeExponent"
    }
    // A factor applied either way, so below 1 it would invert into a *narrowing* of the threshold band and
    // the clamp its KDoc describes would stop being a clamp.
    require(channelSlopeRange >= 1.0) { "channelSlopeRange must be at least 1, was $channelSlopeRange" }
    require(minHeadwaterLength >= 0.0) {
      "minHeadwaterLength must not be negative, was $minHeadwaterLength"
    }
    require(evaporationDepth >= 0.0) { "evaporationDepth must not be negative, was $evaporationDepth" }
    require(widthCoefficient > 0.0) { "widthCoefficient must be positive, was $widthCoefficient" }
    require(depthCoefficient > 0.0) { "depthCoefficient must be positive, was $depthCoefficient" }
    require(shoulderCoefficient >= 0.0) { "shoulderCoefficient must not be negative, was $shoulderCoefficient" }
    require(meanderWidthFactor >= 0.0) { "meanderWidthFactor must not be negative, was $meanderWidthFactor" }
    require(meanderAmplitudeCap >= 0.0) { "meanderAmplitudeCap must not be negative, was $meanderAmplitudeCap" }
    // A divisor of the meander wavelength, and a zero wavelength is an infinite-frequency sine along every
    // river in the world.
    require(meanderWavelengthFactor > 0.0) {
      "meanderWavelengthFactor must be positive, was $meanderWavelengthFactor"
    }
    require(minReachLength >= 0.0) { "minReachLength must not be negative, was $minReachLength" }
    require(confluenceRadiusFactor >= 0.0) {
      "confluenceRadiusFactor must not be negative, was $confluenceRadiusFactor"
    }
  }

  override fun digest() = ParamsDigest()
    .put("runoffCoefficient", runoffCoefficient)
    .put("channelCatchmentArea", channelCatchmentArea)
    .put("aridityExponent", aridityExponent)
    .put("channelSlopeExponent", channelSlopeExponent)
    .put("channelSlopeRange", channelSlopeRange)
    .put("minHeadwaterLength", minHeadwaterLength)
    .put("evaporationDepth", evaporationDepth)
    .put("widthCoefficient", widthCoefficient)
    .put("depthCoefficient", depthCoefficient)
    .put("shoulderCoefficient", shoulderCoefficient)
    .put("minChannelWidthVoxels", minChannelWidthVoxels)
    .put("minChannelDepthVoxels", minChannelDepthVoxels)
    .put("stationSpacing", stationSpacing)
    .put("meanderWidthFactor", meanderWidthFactor)
    .put("meanderAmplitudeCap", meanderAmplitudeCap)
    .put("meanderWavelengthFactor", meanderWavelengthFactor)
    .put("minReachLength", minReachLength)
    .put("confluenceRadiusFactor", confluenceRadiusFactor)
}

/**
 * Stage 4: hydrology. Depression filling, flow routing, lakes, and the river network as vector features.
 *
 * Runs on the eroded surface, so the rivers are in the valleys erosion cut rather than in the valleys
 * the pre-erosion tectonic surface happened to have. Erosion solved its own drainage network on the way
 * there; this solves it once more on the final surface, and that solution is the authoritative one.
 *
 * The output that matters most is not a raster. It is a set of [FeatureKind.RIVER_CHANNEL] features:
 * continuous, resolution-independent centerlines with per-station width, depth and bed elevation, which
 * a chunk two hundred kilometres away can sample and get a channel that lines up with its neighbour's
 * to the millimetre.
 */
class HydrologyStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: HydrologyParams = HydrologyParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion get() = params.digest().value

  /**
   * Note **glacial**, which is what makes a post-glacial river run down the trough it should have inherited.
   *
   * Before it, glacial and hydrology were siblings that neither ordered nor could see one another - they ran
   * in the right order only because the topological sort breaks ties on stage name and `"glacial"` sorts
   * first, which is an alphabetical accident standing where a dependency belongs. Declaring it turns that
   * accident into a guarantee, and lets this stage read the surface ice actually left.
   *
   * It also feeds every stage below: dependency scoping is transitive, so habitability, settlement placement
   * and town layout all reach glacial through this one edge and stop deciding things on ground that is not
   * there.
   */
  override val dependencies =
    listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID, GlacialStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.FLOW_DIRECTION),
    StageOutput.Raster(LayerId.FLOW_ACCUMULATION),
    StageOutput.Raster(LayerId.DISCHARGE),
    StageOutput.Raster(LayerId.WATER_LEVEL),
    StageOutput.Raster(LayerId.LAKE_ID),
    StageOutput.Vector(FeatureKind.RIVER_CHANNEL),
    StageOutput.Vector(FeatureKind.RIVER_CONFLUENCE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val cellArea = metres * metres
    val seaLevel = ctx.config.seaLevel

    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)

    val network = FlowRouting.solve(elevation, seaLevel, metres)

    val drainageArea = network.accumulate { cellArea }
    val discharge = network.accumulate { runoffAt(precipitation.data[it], cellArea) }

    val lakes = Lakes.identify(
      network = network,
      elevation = elevation,
      discharge = discharge,
      seaLevel = seaLevel,
      evaporationDepth = params.evaporationDepth
    )

    val meanPrecipitation = precipitation.mean().coerceAtLeast(1.0)
    // The discharge that the threshold catchment area yields at this world's mean rainfall. Derived rather than
    // configured, so the same number means the same thing on a world of any size or wetness.
    val channelDischarge = runoffAt(
      meanPrecipitation,
      ctx.config.scaleByArea(params.channelCatchmentArea)
    ).coerceAtLeast(MIN_CHANNEL_DISCHARGE)

    // Both modifiers on the threshold are ratios against this world's own mean, so the base threshold keeps
    // meaning "the catchment a channel needs on ordinary ground under ordinary rain" and each term only says
    // how far from ordinary a cell is. Neither can move the river count on its own; that stays
    // channelCatchmentArea's job.
    val slope = landSlopes(elevation, region, metres, seaLevel)
    val meanSlope = meanOverLand(slope, elevation, seaLevel)
    val slopeFloor = 1.0 / params.channelSlopeRange

    val graph = RiverNetwork.extract(
      network = network,
      discharge = discharge,
      lakeId = lakes.lakeId,
      minHeadwaterLength = ctx.config.scaleByLength(params.minHeadwaterLength)
    ) { i ->
      // Higher threshold where it is drier, so the network thins out towards the deserts.
      val aridity =
        (meanPrecipitation / precipitation.data[i].coerceAtLeast(1.0)).pow(params.aridityExponent)

      // Lower threshold where it is steep, so channels rise in the mountains rather than on the plain.
      val steepness = (meanSlope / max(slope.data[i], MIN_SLOPE))
        .pow(params.channelSlopeExponent)
        .coerceIn(slopeFloor, params.channelSlopeRange)

      channelDischarge * aridity * steepness
    }

    val features = buildFeatures(ctx, region, network, discharge, graph)

    val direction = IntGrid(region.width, region.height, network.direction.copyOf())

    return StageResult(
      layers = listOf(
        direction.toLayer(LayerId.FLOW_DIRECTION, region),
        drainageArea.toLayer(LayerId.FLOW_ACCUMULATION, region),
        discharge.toLayer(LayerId.DISCHARGE, region),
        lakes.surface.toLayer(LayerId.WATER_LEVEL, region),
        lakes.lakeId.toLayer(LayerId.LAKE_ID, region)
      ),
      features = features
    )
  }

  /**
   * Ground slope everywhere, with everything below sea level flattened to it.
   *
   * [Grid.gradient] is the wrong instrument for channel initiation because at kilometre cells the steepest
   * ground in the world is the shoreline: a cell of coast beside a cell of shelf at -400 m reads as a slope of
   * 0.4, steeper than any mountain front the erosion model produces. Fed that, the slope term does the exact
   * opposite of its purpose - it makes the coast the *easiest* place in the world to start a channel, and the
   * map fills with combs of parallel streams a few cells long hanging off every shore.
   *
   * Clamping the neighbours at sea level asks the question that was meant: how steep is the land here. A
   * channel head is a subaerial feature, and what is under the water offshore has nothing to do with it.
   */
  private fun landSlopes(elevation: Grid, region: CellRegion, metres: Double, seaLevel: Double): Grid {
    fun dry(x: Int, y: Int) = max(seaLevel, elevation[x, y])

    return Grid(region.width, region.height) { x, y ->
      val dzdx = (dry(x + 1, y) - dry(x - 1, y)) / (2.0 * metres)
      val dzdy = (dry(x, y + 1) - dry(x, y - 1)) / (2.0 * metres)
      sqrt(dzdx * dzdx + dzdy * dzdy)
    }
  }

  /**
   * Mean of [slope] over the cells that are above sea level, or over everything on a world with no land.
   *
   * Land only, because the sea floor is most of a half-water world and it is nearly flat once the shoreline
   * scarp has been clamped out. Averaging it in would drag the reference far below any real hillside and turn
   * a redistribution back into a discount.
   */
  private fun meanOverLand(slope: Grid, elevation: Grid, seaLevel: Double): Double {
    var sum = 0.0
    var count = 0
    for (i in slope.data.indices) {
      if (elevation.data[i] <= seaLevel) continue
      sum += slope.data[i]
      count++
    }
    return if (count == 0) slope.mean().coerceAtLeast(MIN_SLOPE) else (sum / count).coerceAtLeast(MIN_SLOPE)
  }

  /** Runoff from one cell, in cubic metres per second. */
  private fun runoffAt(precipitationMillimetres: Double, cellArea: Double): Double =
    max(0.0, precipitationMillimetres) / 1000.0 * params.runoffCoefficient * cellArea /
        Lakes.SECONDS_PER_YEAR

  /**
   * Turns each reach of the graph into a vector feature, and each confluence into a smoothing disc.
   *
   * Three transformations take the D8 path to a river:
   *
   * 1. **Corner cutting** removes the staircase. A D8 path only ever goes in eight directions, and left
   *    as it is the channel reads as a canal cut by someone with a set square.
   * 2. **Meandering** adds sinuosity, tapered to zero at both ends so the reach still meets its
   *    neighbours exactly.
   * 3. **Station attributes** are interpolated from the per-cell tables by *normalised position along
   *    the reach*, which stays a pure function of arc length - the requirement that makes the whole
   *    thing seam-free.
   */
  private fun buildFeatures(
    ctx: GenContext,
    region: CellRegion,
    network: DrainageNetwork,
    discharge: Grid,
    graph: RiverGraph
  ): List<VectorFeature> {
    val metres = region.resolution.metresPerCell
    val nextId = FeatureIds.allocator(id)
    val features = ArrayList<VectorFeature>(graph.reachCount + graph.confluences.size)

    // The one place the world's voxel size reaches into hydrology: a channel the grid cannot hold is a channel
    // that renders as a dashed line rather than as a river. See ChannelGauge.
    val gauge = ChannelGauge(params, ctx.config.voxelSize)

    fun centreOf(cell: Int) = Vec2d(
      (region.minX + cell % region.width + 0.5) * metres,
      (region.minY + cell / region.width + 0.5) * metres
    )

    for (reach in graph.reaches) {
      val raw = runCatching { Polyline(reach.cells.map(::centreOf)) }.getOrNull() ?: continue
      if (raw.length < params.minReachLength) continue

      val cellCount = reach.cells.size

      // The bed follows the *filled* surface rather than the raw elevation. The fill is monotonically
      // descending along every D8 path by construction, which is exactly the guarantee a river bed
      // needs, and the two surfaces differ only inside depressions - which are lakes, and which the
      // channel mask already excluded.
      val bed = DoubleArray(cellCount)
      var running = Double.MAX_VALUE
      for (k in 0 until cellCount) {
        running = min(running, network.filled.data[reach.cells[k]])
        bed[k] = running
      }

      val flow = DoubleArray(cellCount) { discharge.data[reach.cells[it]] }

      val meanFlow = flow.average()
      val meanWidth = gauge.widthOf(meanFlow)
      val meanSlope = (bed.first() - bed.last()) / raw.length
      val amplitude = Meander.amplitudeFor(
        meanWidth, meanSlope, params.meanderWidthFactor, params.meanderAmplitudeCap
      )
      val wavelength = max(params.stationSpacing * 3.0, meanWidth * params.meanderWavelengthFactor)

      val fine = raw.chaikin(SMOOTHING_PASSES).resample(params.stationSpacing)
      val meanderSeed = GenRng.hash(ctx.seed, id.hash, reach.id.toLong())
      val taper = max(params.minReachLength * 0.35, fine.length * END_TAPER_FRACTION)

      val centerline = fine.offsetLaterally { s ->
        Meander.offset(meanderSeed, s, fine.length, amplitude, wavelength, taper)
      }

      // Normalised position along the reach, which is what the station tables are indexed by. Using the
      // pre-meander length is deliberate: it is a fixed number rather than one that shifts with the
      // offset being computed, so the mapping stays a pure function of arc length.
      val span = fine.length.coerceAtLeast(1e-9)
      fun positionOf(s: Double) = (s / span).coerceIn(0.0, 1.0) * (cellCount - 1)

      features.add(
        LinearFeatures.river(
          id = nextId(),
          centerline = centerline,
          stationSpacing = params.stationSpacing,
          bedElevation = { s -> Tables.linear(bed, positionOf(s)) },
          width = { s -> gauge.widthOf(Tables.linear(flow, positionOf(s))) },
          depth = { s -> gauge.depthOf(Tables.linear(flow, positionOf(s))) },
          shoulder = { s -> gauge.shoulderOf(Tables.linear(flow, positionOf(s))) }
        )
      )
    }

    for (cell in graph.confluences) {
      val flow = discharge.data[cell]
      val width = gauge.widthOf(flow)
      val depth = gauge.depthOf(flow)
      val floor = network.filled.data[cell] - depth
      val radius = max(params.stationSpacing, width * params.confluenceRadiusFactor)

      features.add(
        PointFeature(
          id = nextId(),
          kind = FeatureKind.RIVER_CONFLUENCE,
          center = centreOf(cell),
          radius = radius,
          // A shallow bowl whose rim reaches back up to the bank top. Stamped above both reaches, so it
          // replaces the crease that `min` of two parabolic channels leaves along the bisector of the Y.
          profile = RadialProfiles.bowl(floor, depth, radius, exponent = 2.0)
        )
      )
    }

    return features
  }

  companion object {
    val ID = StageId("hydrology")

    /**
     * Floor on the derived channel threshold, in cubic metres per second.
     *
     * A world that is both tiny and arid can derive a threshold so low that every cell qualifies, and a raster
     * where every cell is a river is not a drainage network - it is a flooded plain with a graph over it, and
     * the feature count that comes out of it will exhaust memory before anybody looks at it.
     */
    const val MIN_CHANNEL_DISCHARGE = 0.02

    /** Slope floor for the channel-initiation term, so a dead-flat cell cannot divide by zero. */
    private const val MIN_SLOPE = 1e-4

    private const val SMOOTHING_PASSES = 2

    /** Fraction of a reach's length at each end over which the meander fades out. */
    private const val END_TAPER_FRACTION = 0.16
  }
}
