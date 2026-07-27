package net.bestia.worldgen.geo

import net.bestia.worldgen.bio.BiomeStage
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
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Tables
import net.bestia.worldgen.hydro.FlowRouting
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max
import kotlin.math.min

/** Tuning for [GlacialStage]. */
data class GlacialParams(

  /** Mean annual temperature at or below which snow accumulates rather than melting. */
  val snowlineTemperature: Double = -1.5,

  /** Minimum annual precipitation for ice to build up. Cold and dry is a polar desert, not a glacier. */
  val minPrecipitation: Double = 160.0,

  /** Ice accumulation per year in metres of equivalent thickness at full accumulation. */
  val accumulationRate: Double = 1.2,

  /** Relaxation passes for the shallow-ice flow approximation. */
  val flowIterations: Int = 40,

  /** Ice thickness in metres below which a cell is snowfield rather than a flowing glacier. */
  val minIceThickness: Double = 22.0,

  /** Ice flux at which a flowline becomes a trough worth carving. */
  val troughFlux: Double = 2.0e7,

  /** Minimum trough length in metres. Shorter than this is a cirque, not a valley. */
  val minTroughLength: Double = 6_000.0,

  /** Half-width of a trough floor as a multiple of the cube root of its ice flux. */
  val floorWidthFactor: Double = 5.5,

  /** Ratio of total trough half-width to floor half-width. Sets how far the walls lean out. */
  val wallSpread: Double = 2.6,

  /** Power of the U profile. 2 is a classic U; higher gives the near-vertical walls of a young trough. */
  val wallExponent: Double = 2.4,

  /** Metres of overdeepening per unit of normalised erosion. What makes the floor undulate and hold tarns. */
  val overdeepening: Double = 90.0,

  /** Cirque radius as a multiple of the trough's floor half-width. */
  val cirqueRadiusFactor: Double = 3.2,

  /** Height of a terminal moraine in metres. */
  val moraineHeight: Double = 28.0
)

/**
 * Stage 9: glacial erosion, as vector features.
 *
 * This is the case the whole three-representation split exists for. A kilometre raster physically cannot
 * hold a glacial trough: real troughs are one to three kilometres wide *in total*, and every trait that makes
 * one recognisable - the flat floor, the near-vertical walls, the truncated spurs, the hanging tributaries -
 * is sub-cell. A three-cell U kernel at kilometre resolution produces a three kilometre gouge with a
 * one-cell floor, which is not a trough; it is a dent.
 *
 * So it runs as two passes, exactly as the architecture document sets out.
 *
 * The **coarse pass** decides where ice is and how it moves: accumulate where it is cold and wet enough, flow
 * downhill by the ice surface gradient, and accumulate flux along the flow network. It only needs to be
 * roughly right, because it is deciding *where* glaciers are and not what they look like. The ice dynamics
 * are approximate as a result - the coarse pass cannot see the fine geometry it is implying - and for a game
 * that is entirely fine.
 *
 * The **vector extraction** traces the ice flowlines into trough centerlines with per-station floor elevation
 * and U-profile parameters, and the profile is then applied analytically at whatever resolution the chunk
 * wants. Truncated spurs fall out for free, because the trough carves straight through any ridge crossing it.
 * Hanging valleys fall out for free, because a tributary's floor elevation is set independently of the trunk's.
 *
 * Fjords are drowned troughs: the same feature with its floor below sea level. Cirques are the bowls at the
 * heads, and moraines the ridges at the snouts.
 */
class GlacialStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: GlacialParams = GlacialParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val dependencies = listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.ICE_THICKNESS),
    StageOutput.Vector(FeatureKind.GLACIAL_TROUGH),
    StageOutput.Vector(FeatureKind.FJORD),
    StageOutput.Vector(FeatureKind.CIRQUE),
    StageOutput.Vector(FeatureKind.MORAINE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val temperature = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE), region)
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)

    val ice = accumulate(region, elevation, temperature, precipitation, metres)
    val features = if (ice.data.any { it >= params.minIceThickness }) {
      extract(ctx, region, elevation, ice, seaLevel, metres)
    } else {
      // A world with no ice at all is a perfectly good world. Glaciation is the optional stage.
      emptyList()
    }

    return StageResult(
      layers = listOf(ice.toLayer(LayerId.ICE_THICKNESS, region)),
      features = features
    )
  }

  /**
   * The coarse pass: where ice is, and how thick.
   *
   * A shallow-ice approximation reduced to its essentials. Ice accumulates where the climate allows, then
   * relaxes downhill along the gradient of the *ice surface* - bedrock plus ice - which is what makes a
   * glacier thicken in a valley and thin on a ridge, and what lets it flow across a minor rise if there is
   * enough of it behind.
   */
  private fun accumulate(
    region: CellRegion,
    elevation: Grid,
    temperature: Grid,
    precipitation: Grid,
    metres: Double
  ): Grid {
    val ice = Grid(region.width, region.height)

    for (i in ice.data.indices) {
      if (temperature.data[i] > params.snowlineTemperature) continue
      if (precipitation.data[i] < params.minPrecipitation) continue

      // Colder and wetter accumulates faster, and both matter - the coldest place on the map is often the
      // driest, which is why the interior of an ice cap is thinner than its margins.
      val cold = ((params.snowlineTemperature - temperature.data[i]) / COLD_SCALE).coerceIn(0.0, 1.0)
      val wet = (precipitation.data[i] / WET_SCALE).coerceIn(0.0, 1.0)
      ice.data[i] = params.accumulationRate * cold * wet * ICE_YEARS
    }

    // Relaxation towards a surface that slopes downhill. Double buffered, so the result cannot depend on the
    // order cells are visited in.
    val delta = DoubleArray(ice.size)
    repeat(params.flowIterations) {
      java.util.Arrays.fill(delta, 0.0)

      for (y in 1 until region.height - 1) {
        for (x in 1 until region.width - 1) {
          val i = y * region.width + x
          if (ice.data[i] <= 0.0) continue

          val here = elevation.data[i] + ice.data[i]
          var lowestSurface = here
          var target = -1

          for (d in 0 until 8) {
            val j = (y + D8.DY[d]) * region.width + (x + D8.DX[d])
            val surface = elevation.data[j] + ice.data[j]
            if (surface < lowestSurface) {
              lowestSurface = surface
              target = j
            }
          }

          if (target < 0) continue

          // Move enough to halve the surface difference, capped by how much ice is actually there.
          val move = min(ice.data[i], (here - lowestSurface) * FLOW_RATE)
          delta[i] -= move
          delta[target] += move
        }
      }

      for (i in ice.data.indices) {
        ice.data[i] = (ice.data[i] + delta[i]).coerceAtLeast(0.0)
      }
    }

    // Ice below sea level has calved away; a floating shelf is not a landform this pipeline models.
    for (i in ice.data.indices) {
      if (elevation.data[i] < 0.0 && ice.data[i] < CALVING_THICKNESS) ice.data[i] = 0.0
    }

    return ice
  }

  /**
   * Traces ice flowlines into troughs, and hangs the cirques and moraines off them.
   *
   * Flow direction comes from the *ice surface* rather than the bedrock, which is the whole reason a glacier
   * goes where it does: it will ride over a bedrock rise its own thickness can bridge, and that is what makes
   * a trough cut across a spur instead of going round it.
   */
  private fun extract(
    ctx: GenContext,
    region: CellRegion,
    elevation: Grid,
    ice: Grid,
    seaLevel: Double,
    metres: Double
  ): List<VectorFeature> {
    val surface = Grid(region.width, region.height) { x, y ->
      val i = y * region.width + x
      elevation.data[i] + ice.data[i]
    }

    val network = FlowRouting.solve(surface, Double.NEGATIVE_INFINITY, metres)
    val cellArea = metres * metres
    val flux = network.accumulate { if (ice.data[it] >= params.minIceThickness) ice.data[it] * cellArea else 0.0 }

    val glaciated = BooleanArray(ice.size) { ice.data[it] >= params.minIceThickness }
    val channel = BooleanArray(ice.size) { glaciated[it] && flux.data[it] >= params.troughFlux }

    // A trough runs from a source - a glaciated cell with no glaciated cell above it - to wherever the ice
    // stops. The same reach-tracing shape as the river network, on a different network.
    val donors = IntArray(ice.size)
    for (i in 0 until ice.size) {
      if (!channel[i]) continue
      val r = network.receiver[i]
      if (r != i && channel[r]) donors[r]++
    }

    val nextId = FeatureIds.allocator(id)
    val features = ArrayList<VectorFeature>()

    for (start in 0 until ice.size) {
      if (!channel[start] || donors[start] != 0) continue

      val path = ArrayList<Int>()
      var current = start
      while (channel[current]) {
        path.add(current)
        val r = network.receiver[current]
        if (r == current) break
        current = r
        // Stop where the ice ran out: that is the snout, and where the moraine goes.
        if (!channel[current]) {
          path.add(current)
          break
        }
      }

      if (path.size < 3) continue

      val centre = { cell: Int ->
        Vec2d(
          (region.minX + cell % region.width + 0.5) * metres,
          (region.minY + cell / region.width + 0.5) * metres
        )
      }

      val raw = runCatching { Polyline(path.map(centre)) }.getOrNull() ?: continue
      if (raw.length < params.minTroughLength) continue

      val line = raw.chaikin(SMOOTHING).resample(STATION_SPACING)
      features.add(troughFeature(nextId(), line, path, region, elevation, flux, seaLevel, metres))
      features.add(cirqueFeature(nextId(), centre(path.first()), path, elevation, flux, region))
      moraineFeature(nextId(), line, path, region, elevation, metres)?.let { features.add(it) }
    }

    return features
  }

  /**
   * One trough, as a [FeatureKind.GLACIAL_TROUGH] or - where its floor is below sea level - a
   * [FeatureKind.FJORD].
   *
   * The floor is *overdeepened* in proportion to ice flux, which is what makes it undulate rather than fall
   * monotonically like a river bed. That undulation is the diagnostic difference between a glacial valley and
   * a fluvial one, and it is what leaves ribbon lakes strung along the floor. It also produces a fjord's sill
   * for free: the mouth carries less ice than the middle, so it is eroded less, so it stands higher than the
   * basins behind it - which is exactly what a sill is.
   */
  private fun troughFeature(
    id: FeatureId,
    line: Polyline,
    path: List<Int>,
    region: CellRegion,
    elevation: Grid,
    flux: Grid,
    seaLevel: Double,
    metres: Double
  ): VectorFeature {
    val count = path.size

    // Per-cell tables, read by normalised position along the trough - the same scheme the river reaches use,
    // and for the same reason: it stays a pure function of arc length.
    val bed = DoubleArray(count)
    val floorHalf = DoubleArray(count)
    val erosion = DoubleArray(count)

    var running = Double.MAX_VALUE
    for (k in 0 until count) {
      val cell = path[k]
      val strength = normalisedFlux(flux.data[cell])
      erosion[k] = strength
      floorHalf[k] = max(metres * 0.12, params.floorWidthFactor * Math.cbrt(flux.data[cell].coerceAtLeast(1.0)))

      // Monotonic before overdeepening, so the trough still descends overall; the overdeepening is then
      // subtracted on top and is what breaks the monotonicity locally.
      running = min(running, elevation.data[cell])
      bed[k] = running - params.overdeepening * strength
    }

    val span = line.length.coerceAtLeast(1.0)
    fun position(s: Double) = (s / span).coerceIn(0.0, 1.0) * (count - 1)

    // A fjord is a *drowned* trough, so the test is on the snout rather than on the deepest point. A trough
    // whose middle dips below sea level while its mouth is a hundred metres up is an overdeepened inland
    // valley holding a ribbon lake - which is a different landform, and calling it a fjord would put sills
    // and tidewater a long way from any sea.
    val kind = if (bed[count - 1] < seaLevel) FeatureKind.FJORD else FeatureKind.GLACIAL_TROUGH

    return LinearFeatures.glacialTrough(
      id = id,
      centerline = line,
      stationSpacing = STATION_SPACING,
      kind = kind,
      floorElevation = { s -> Tables.linear(bed, position(s)) },
      halfWidthFloor = { s -> Tables.linear(floorHalf, position(s)) },
      halfWidth = { s -> Tables.linear(floorHalf, position(s)) * params.wallSpread },
      wallHeight = { s ->
        // Wall height is what the trough has to climb back to: the difference between the surrounding ground
        // and the floor it carved.
        val at = position(s)
        val cell = path[at.toInt().coerceIn(0, count - 1)]
        max(0.0, elevation.data[cell] - Tables.linear(bed, at)) + WALL_HEADROOM
      },
      wallExponent = { params.wallExponent }
    )
  }

  /**
   * The cirque at the head: an armchair bowl where the ice began.
   *
   * A radial feature rather than a linear one, because that is its actual shape - it is the place the trough
   * starts rather than part of its length, and stamping it as a disc gives the steep headwall that a linear
   * profile tapering to nothing cannot.
   */
  private fun cirqueFeature(
    id: FeatureId,
    position: Vec2d,
    path: List<Int>,
    elevation: Grid,
    flux: Grid,
    region: CellRegion
  ): VectorFeature {
    val head = path.first()
    val strength = normalisedFlux(flux.data[head])
    val floorHalf = max(
      region.resolution.metresPerCell * 0.15,
      params.floorWidthFactor * Math.cbrt(flux.data[head].coerceAtLeast(1.0))
    )
    val radius = floorHalf * params.cirqueRadiusFactor
    val floor = elevation.data[head] - params.overdeepening * strength * CIRQUE_DEEPENING

    return PointFeature(
      id = id,
      kind = FeatureKind.CIRQUE,
      center = position,
      radius = radius,
      // A steep-sided bowl: a high exponent keeps the floor flat and stands the headwall up, which is what
      // makes a cirque hold a tarn.
      profile = RadialProfiles.bowl(floor, elevation.data[head] - floor + WALL_HEADROOM, radius, exponent = 3.0),
      edgeFraction = 0.2
    )
  }

  /**
   * The terminal moraine: the ridge of debris dumped where the ice stopped.
   *
   * Additive rather than subtractive, and stamped across the snout rather than along the trough - a moraine is
   * a dam across the valley mouth, which is why so many of them hold a lake behind them.
   */
  private fun moraineFeature(
    id: FeatureId,
    line: Polyline,
    path: List<Int>,
    region: CellRegion,
    elevation: Grid,
    metres: Double
  ): VectorFeature? {
    if (line.length < params.minTroughLength) return null

    val snout = line.pointAt(line.length)
    val bearing = line.tangentAt(line.length)
    val across = bearing.perpendicular()
    val width = max(metres * 0.6, MORAINE_SPAN)

    val ridge = runCatching {
      Polyline(listOf(snout - across * width, snout, snout + across * width))
    }.getOrNull() ?: return null

    return LinearFeatures.moraine(
      id = id,
      centerline = ridge,
      halfWidth = { MORAINE_HALF_WIDTH },
      ridgeHeight = { params.moraineHeight }
    )
  }

  /** Ice flux mapped into `[0,1]`, so erosion strength is comparable between a cirque and a trunk trough. */
  private fun normalisedFlux(flux: Double): Double =
    (Math.log10(1.0 + flux) / FLUX_LOG_SCALE).coerceIn(0.0, 1.0)

  companion object {
    val ID = StageId("glacial")

    private const val SMOOTHING = 2

    /** Station spacing along a trough centerline, in metres. The doc's figure. */
    private const val STATION_SPACING = 100.0

    /** Degrees below the snowline at which accumulation is at its maximum. */
    private const val COLD_SCALE = 12.0

    /** Annual precipitation at which accumulation is at its maximum, in millimetres. */
    private const val WET_SCALE = 1_400.0

    /** Years of accumulation the coarse pass represents. Sets the overall ice thickness scale. */
    private const val ICE_YEARS = 320.0

    /** Fraction of the ice-surface difference moved per relaxation pass. Above 0.5 it oscillates. */
    private const val FLOW_RATE = 0.22

    /** Ice thinner than this below sea level has calved away. */
    private const val CALVING_THICKNESS = 260.0

    /** Metres of wall above the surrounding ground, so a trough rim is a rim rather than a step. */
    private const val WALL_HEADROOM = 12.0

    /** How much more a cirque is overdeepened than the trough below it. */
    private const val CIRQUE_DEEPENING = 1.4

    private const val MORAINE_SPAN = 700.0
    private const val MORAINE_HALF_WIDTH = 220.0

    /** log10 of the ice flux that counts as "as erosive as it gets". */
    private const val FLUX_LOG_SCALE = 9.5
  }
}
