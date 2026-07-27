package net.bestia.worldgen.hydro

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
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
   */
  val channelCatchmentArea: Double = 93_000_000.0,

  /**
   * How strongly the channel threshold rises in dry regions.
   *
   * Scaling the threshold by local rainfall rather than using one figure everywhere is what gives an
   * arid region sparse drainage. With a fixed threshold a desert gets the same dense dendritic network
   * as a rainforest, only with less water in it, and the map stops distinguishing them.
   */
  val aridityExponent: Double = 1.0,

  /** Metres of water evaporated from a lake surface per year. Decides which basins are salt lakes. */
  val evaporationDepth: Double = 1.1,

  /** Hydraulic geometry: `width = a * Q^0.5`. */
  val widthCoefficient: Double = 4.2,

  /** Hydraulic geometry: `depth = b * Q^0.4`. */
  val depthCoefficient: Double = 0.36,

  /** Floodplain half-width coefficient: `shoulder = c * Q^0.35`. */
  val shoulderCoefficient: Double = 24.0,

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
) {
  init {
    require(runoffCoefficient in 0.0..1.0) { "runoffCoefficient must be in [0,1]" }
    require(channelCatchmentArea > 0.0) { "channelCatchmentArea must be positive" }
    require(stationSpacing > 0.0) { "stationSpacing must be positive" }
  }
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
  override val dependencies = listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID)
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

    val graph = RiverNetwork.extract(network, discharge, lakes.lakeId) { i ->
      // Higher threshold where it is drier, so the network thins out towards the deserts.
      channelDischarge *
          (meanPrecipitation / precipitation.data[i].coerceAtLeast(1.0)).pow(params.aridityExponent)
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
      val meanWidth = widthOf(meanFlow)
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
          width = { s -> widthOf(Tables.linear(flow, positionOf(s))) },
          depth = { s -> depthOf(Tables.linear(flow, positionOf(s))) },
          shoulder = { s -> shoulderOf(Tables.linear(flow, positionOf(s))) }
        )
      )
    }

    for (cell in graph.confluences) {
      val flow = discharge.data[cell]
      val width = widthOf(flow)
      val depth = depthOf(flow)
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

  /** Hydraulic geometry: wetted width in metres from discharge in cubic metres per second. */
  private fun widthOf(discharge: Double) =
    params.widthCoefficient * discharge.coerceAtLeast(0.0).pow(0.5)

  private fun depthOf(discharge: Double) =
    params.depthCoefficient * discharge.coerceAtLeast(0.0).pow(0.4)

  private fun shoulderOf(discharge: Double) =
    max(widthOf(discharge), params.shoulderCoefficient * discharge.coerceAtLeast(0.0).pow(0.35))

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

    private const val SMOOTHING_PASSES = 2

    /** Fraction of a reach's length at each end over which the meander fades out. */
    private const val END_TAPER_FRACTION = 0.16
  }
}
