package net.bestia.worldgen.geo

import net.bestia.worldgen.bio.BiomeStage
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
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.fields.Tables
import net.bestia.worldgen.hydro.FlowRouting
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.HeightModSink
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.ceil
import kotlin.math.floor
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

  /**
   * Largest a trough floor may be, in metres of half-width.
   *
   * The same unbounded cube root as [maxCirqueRadius], and it had the same consequence one scale up. This
   * class's own opening paragraph says real troughs are *one to three kilometres wide in total*; measured
   * before this cap, the corridor half-widths on the reference world ran to a **median of 8.7 km, a ninetieth
   * percentile of 45 km and a maximum of 93 km.** A trough 186 km across is not a landform, it is a continent
   * with a dent in it.
   *
   * It went unnoticed for as long as it did because nothing but chunk generation ever read a trough, and a
   * chunk is 32 m wide - at that scale an impossibly broad valley floor looks like ordinary flat ground. It
   * became visible the moment the carve reached the raster: the troughs' bounding boxes summed to thirty-three
   * times the area of the world, and drowned four points of its land.
   *
   * 900 m of floor gives `wallSpread` a total half-width of about 2.3 km, so a major valley is a shade under
   * five kilometres across - the upper end of what the literature describes, which is right for the largest
   * trough on a world rather than the typical one.
   */
  val maxFloorHalfWidth: Double = 900.0,

  /** Ratio of total trough half-width to floor half-width. Sets how far the walls lean out. */
  val wallSpread: Double = 2.6,

  /** Power of the U profile. 2 is a classic U; higher gives the near-vertical walls of a young trough. */
  val wallExponent: Double = 2.4,

  /** Metres of overdeepening per unit of normalised erosion. What makes the floor undulate and hold tarns. */
  val overdeepening: Double = 90.0,

  /** Cirque radius as a multiple of the trough's floor half-width. */
  val cirqueRadiusFactor: Double = 3.2,

  /**
   * Largest a cirque may be, in metres of radius.
   *
   * A cirque is the armchair hollow a glacier gouges at the head of its own valley, and on Earth the big ones
   * are a kilometre or two across - the landform is bounded by how much ice can sit in one bowl before it
   * starts flowing out as a valley glacier instead.
   *
   * Nothing bounded it here. The radius is `floorWidthFactor * cbrt(flux) * cirqueRadiusFactor`, and a cube
   * root grows slowly but never stops, so a world with big enough mountains produced cirques **twelve
   * kilometres in radius** - a fifth of a 128 km world, drawn on the map as a great pale circle lying half out
   * to sea. That is not a cirque, and the bowl profile it stamps is a crater.
   */
  val maxCirqueRadius: Double = 1_800.0,

  /** Height of a terminal moraine in metres. */
  val moraineHeight: Double = 28.0
) : Params {

  init {
    require(snowlineTemperature.isFinite()) { "snowlineTemperature must be finite, was $snowlineTemperature" }
    require(minPrecipitation >= 0.0) { "minPrecipitation must not be negative, was $minPrecipitation" }
    require(accumulationRate >= 0.0) { "accumulationRate must not be negative, was $accumulationRate" }
    require(flowIterations >= 0) { "flowIterations must not be negative, was $flowIterations" }
    require(minIceThickness >= 0.0) { "minIceThickness must not be negative, was $minIceThickness" }
    require(troughFlux > 0.0) { "troughFlux must be positive, was $troughFlux" }
    require(minTroughLength >= 0.0) { "minTroughLength must not be negative, was $minTroughLength" }
    require(floorWidthFactor > 0.0) { "floorWidthFactor must be positive, was $floorWidthFactor" }
    // The two caps are the whole reason this stage stopped drowning four points of the world's land - see
    // their KDoc. A cap of zero collapses the profile it bounds, so both are strictly positive.
    require(maxFloorHalfWidth > 0.0) { "maxFloorHalfWidth must be positive, was $maxFloorHalfWidth" }
    require(maxCirqueRadius > 0.0) { "maxCirqueRadius must be positive, was $maxCirqueRadius" }
    // A trough is a floor plus leaning walls, so the total half-width cannot be less than the floor's.
    require(wallSpread >= 1.0) { "wallSpread must be at least 1, was $wallSpread" }
    require(wallExponent > 0.0) { "wallExponent must be positive, was $wallExponent" }
    require(overdeepening >= 0.0) { "overdeepening must not be negative, was $overdeepening" }
    require(cirqueRadiusFactor > 0.0) { "cirqueRadiusFactor must be positive, was $cirqueRadiusFactor" }
    require(moraineHeight >= 0.0) { "moraineHeight must not be negative, was $moraineHeight" }
  }

  override fun digest() = ParamsDigest()
    .put("snowlineTemperature", snowlineTemperature)
    .put("minPrecipitation", minPrecipitation)
    .put("accumulationRate", accumulationRate)
    .put("flowIterations", flowIterations)
    .put("minIceThickness", minIceThickness)
    .put("troughFlux", troughFlux)
    .put("minTroughLength", minTroughLength)
    .put("floorWidthFactor", floorWidthFactor)
    .put("maxFloorHalfWidth", maxFloorHalfWidth)
    .put("wallSpread", wallSpread)
    .put("wallExponent", wallExponent)
    .put("overdeepening", overdeepening)
    .put("cirqueRadiusFactor", cirqueRadiusFactor)
    .put("maxCirqueRadius", maxCirqueRadius)
    .put("moraineHeight", moraineHeight)
}

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

  // 2: emits ELEVATION - the troughs are carved into the raster, not only stamped at chunk time.
  override val version = 2

  override val paramsVersion get() = params.digest().value
  override val dependencies = listOf(TectonicsStage.ID, ClimateStage.ID, ErosionStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Raster(LayerId.ELEVATION),
    StageOutput.Raster(LayerId.ICE_THICKNESS),
    StageOutput.Vector(FeatureKind.GLACIAL_TROUGH),
    StageOutput.Vector(FeatureKind.FJORD),
    StageOutput.Vector(FeatureKind.CIRQUE),
    StageOutput.Vector(FeatureKind.MORAINE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val elevation = Grid.from(ctx.layers.float(LayerId.ERODED_ELEVATION))
    val temperature = Grid.resampled(ctx.layers.float(LayerId.TEMPERATURE), region)
    val precipitation = Grid.resampled(ctx.layers.float(LayerId.PRECIPITATION), region)

    val ice = accumulate(region, elevation, temperature, precipitation, metres)
    val features = if (ice.data.any { it >= params.minIceThickness }) {
      Timings.measure("glacial.extract") { extract(ctx, region, elevation, ice, seaLevel, metres) }
    } else {
      // A world with no ice at all is a perfectly good world. Glaciation is the optional stage.
      emptyList()
    }

    // The fluvial surface with the ice's own work cut into it. On an ice-free world this is a straight copy,
    // which is why the layer is emitted unconditionally: a downstream stage must never have to ask whether
    // this world had glaciers before it knows which layer holds the ground.
    Timings.measure("glacial.carveInto") { carveInto(elevation, features, region, metres) }

    return StageResult(
      layers = listOf(
        elevation.toLayer(LayerId.ELEVATION, region),
        ice.toLayer(LayerId.ICE_THICKNESS, region)
      ),
      features = features
    )
  }

  /**
   * Rasterises the carving features onto [elevation], in place.
   *
   * ### Why the coarse tier needs this at all
   *
   * A trough is a vector feature evaluated at chunk scale, which is what makes its cross-section crisp at any
   * resolution, and for a long time that was the whole story. The trouble is that *deciding* things is done on
   * the raster: flow routing, habitability, settlement placement and town layout all read [LayerId.ELEVATION]
   * and none of them can see a feature. So a trough that cut four hundred metres out of a valley was invisible
   * to every one of them until chunk generation, at which point the decisions were already made - rivers routed
   * over ground that is not there, and a town laid out on a surface the finished chunks cut away beneath it,
   * leaving its buildings on plinths hundreds of metres tall.
   *
   * The design's own rule resolves it: *the coarse pass decides where a feature goes and how big it is, the
   * fine pass decides what it looks like.* This is the coarse half, and the vector tier keeps the fine half.
   *
   * ### Why applying it twice is harmless
   *
   * Chunk generation stamps these same features over a base sampled from the layer this writes, so every
   * carve happens twice. That is safe **only** for features that impose an absolute height under a `MIN`
   * blend, which is exactly what a trough, a fjord and a cirque do: `Profiles.glacialTrough` ignores the base
   * height entirely and `MIN` keeps the lower of the two, so `min(floor, floor)` is `floor` and the second
   * application changes nothing.
   *
   * A moraine is the counter-example and the reason this filters rather than taking every feature: it is a
   * ridge blended with `ADD`, so carving it here and stamping it again at chunk time would build it twice as
   * tall. Filtering on the blend mode rather than on the feature kind keeps that reasoning attached to the
   * property it depends on, so a new additive glacial feature is excluded automatically.
   *
   * The one visible consequence is that the coarse carve is bicubically smeared when the chunk tier samples
   * it, so the ground flanking a trough dips a little more gently than the vector profile alone would say.
   * That is the raster tier doing what the design says it does to any feature narrower than a few cells, and
   * for a glacial valley - which really is broader than its own trough floor - it errs in the right direction.
   *
   * ### Accumulated in place, in the evaluator's own order
   *
   * The obvious implementation - query a [FeatureIndex] per cell and hand the hits to a [FeatureEvaluator] -
   * is what this did first, and it cost twenty-four times the rest of the stage: a list allocation, a sort and
   * a scratch buffer per cell, several hundred thousand times over.
   *
   * Sorting the features **once** by `(priority, id)` and accumulating each one into the grid in place is
   * exactly equivalent, because that is the order `FeatureEvaluator` would have imposed anyway and each
   * feature sees the height its predecessors left - which is the evaluator's contract. So the arithmetic below
   * mirrors `FeatureEvaluator.add`'s `MIN` case rather than reimplementing a carve, and one sink and one
   * scratch buffer serve the whole world.
   */
  private fun carveInto(
    elevation: Grid,
    features: List<VectorFeature>,
    region: CellRegion,
    metres: Double
  ) {
    val carving = features
      .filter { it.affectsHeight && it.blend == BlendMode.MIN }
      .sortedWith(compareBy({ it.priority }, { it.id.value }))
    if (carving.isEmpty()) return

    check(carving.none { !isRasterisable(it) }) {
      "${carving.first { !isRasterisable(it) }} reached the coarse carve. This path walks outline() and " +
          "stamps a corridorWidthMax band along it, which on a closed ring paints the rim and leaves the " +
          "interior untouched - a lake with a shore and no water in it. Widening corridorWidthMax is not " +
          "the repair; it is documented as a bound on influence and the spatial index trusts it. An areal " +
          "feature that genuinely needs to reach the raster wants an interior scanline rasteriser."
    }

    val originX = region.minX * metres
    val originY = region.minY * metres

    val scratch = DoubleArray(carving.maxOf { it.scratchSize })
    val sink = MinBlendSink()

    // Which feature last claimed each cell, so the corridor walk below can revisit a cell cheaply without
    // evaluating it twice. One array for the whole world rather than a set per feature.
    val claimed = IntArray(elevation.size) { -1 }

    fun evaluate(feature: VectorFeature, ordinal: Int, cellX: Int, cellY: Int) {
      if (cellX < 0 || cellY < 0 || cellX >= region.width || cellY >= region.height) return
      val i = elevation.index(cellX, cellY)
      if (claimed[i] == ordinal) return
      claimed[i] = ordinal

      sink.height = elevation.data[i]
      feature.evaluateColumn(
        originX + (cellX + 0.5) * metres,
        originY + (cellY + 0.5) * metres,
        sink.height,
        scratch,
        sink
      )
      elevation.data[i] = sink.height
    }

    for ((ordinal, feature) in carving.withIndex()) {
      val box = feature.bbox
      val minCellX = floor((box.minX - originX) / metres).toInt()
      val maxCellX = ceil((box.maxX - originX) / metres).toInt()
      val minCellY = floor((box.minY - originY) / metres).toInt()
      val maxCellY = ceil((box.maxY - originY) / metres).toInt()

      val outline = feature.outline()
      val boxCells = (maxCellX - minCellX + 1).toLong() * (maxCellY - minCellY + 1).toLong()

      // A compact feature - a cirque is a couple of cells across - is cheaper to sweep than to trace.
      if (outline.isEmpty() || boxCells <= COMPACT_FEATURE_CELLS) {
        for (cellY in minCellY..maxCellY) {
          for (cellX in minCellX..maxCellX) evaluate(feature, ordinal, cellX, cellY)
        }
        continue
      }

      // A trough is a long thin thing lying diagonally across a large and mostly empty bounding box, and
      // `evaluateColumn` costs a projection against every segment of the centreline whether the cell is in the
      // corridor or a corner of the box twenty kilometres away. Tracing the geometry instead visits the
      // corridor and nothing else: measured on the reference world, sweeping boxes cost this stage twenty-one
      // times what the ice flow itself did.
      val reach = feature.corridorWidthMax + metres
      val span = ceil(reach / metres).toInt()

      for (line in outline) {
        var along = 0.0
        while (true) {
          val at = line.pointAt(along)
          val centreX = floor((at.x - originX) / metres).toInt()
          val centreY = floor((at.y - originY) / metres).toInt()

          for (dy in -span..span) {
            for (dx in -span..span) evaluate(feature, ordinal, centreX + dx, centreY + dy)
          }

          if (along >= line.length) break
          along = min(along + metres * 0.5, line.length)
        }
      }
    }
  }

  /**
   * One column's worth of `MIN` blending, reused across every cell.
   *
   * `FeatureEvaluator.add`'s `MIN` case, extracted so it can be called without allocating an evaluator per
   * cell. It is a copy of three lines, and the reason that is acceptable here where it usually is not: the
   * features this serves are filtered to `MIN` beforehand, so there is no branch to get wrong, and
   * `ProfileContinuityTest` pins the property that actually matters - that the coarse carve and the chunk-tier
   * stamp of the same feature agree.
   */
  private class MinBlendSink : HeightModSink {
    var height = 0.0

    override fun add(
      featureId: FeatureId,
      priority: Int,
      blend: BlendMode,
      value: Double,
      weight: Double
    ) {
      val w = min(1.0, weight)
      val target = if (value < height) value else height
      height += (target - height) * w
    }
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
    Timings.measure("glacial.iceFlow") {
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
    // Both thresholds are gated on the world being big enough to grow a real glacier, so both are scaled: flux
    // as an area (it accumulates over cells), trough length as a length.
    val troughFlux = ctx.config.scaleByArea(params.troughFlux)
    val minTroughLength = ctx.config.scaleByLength(params.minTroughLength)
    val channel = BooleanArray(ice.size) { glaciated[it] && flux.data[it] >= troughFlux }

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

    // No junction feature here, and that is a measured decision rather than an omission.
    //
    // Rivers need one: `HydrologyStage` stamps a `RIVER_CONFLUENCE` bowl over every cell with two channel
    // donors, because `min` of two parabolic *channel* cuts leaves a bar of land down the middle of the merged
    // channel, where there should be open water. Troughs are traced the same way - source to snout, `donors`
    // counted identically - so the same fix looks like it should apply, and it was planned.
    //
    // It does not apply, for two reasons that both survive checking. **Where the floors merge they merge at the
    // same level**, so `min` of two flat floors is that floor and there is nothing to smooth; and **the wedge
    // above them is a spur**, which is the landform this class's own note claims ("truncated spurs fall out for
    // free") rather than an artefact. A sharp crest between two converging valleys is an arête.
    //
    // Measured on the 128-cell reference world, which has five junction cells: curvature along a transect
    // through each junction against a control transect 1 200 m away, at 25 m sampling. The junction transects
    // were **no rougher than the controls, and in four of five cases smoother** - maxima of 0.55 vs 0.89, 0.73
    // vs 0.88, 0.44 vs 0.76, 2.36 vs 7.73 and 0.62 vs 1.83 metres of second difference. A `min` crease across
    // valleys kilometres wide would have shown as a spike of metres, which the 7.73 m control proves the
    // measurement can see.
    //
    // A bowl here would also have had to avoid erasing hanging valleys, which exist precisely because a
    // tributary's floor is a running minimum over its own path and so sits *above* the trunk's.
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
      if (raw.length < minTroughLength) continue

      val line = raw.chaikin(SMOOTHING).resample(STATION_SPACING)
      features.add(troughFeature(nextId(), line, path, region, elevation, flux, seaLevel, metres))
      features.add(cirqueFeature(nextId(), centre(path.first()), path, elevation, flux, region))
      moraineFeature(nextId(), line, path, region, elevation, metres, minTroughLength)?.let { features.add(it) }
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
        .coerceAtMost(params.maxFloorHalfWidth)

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
    // Capped, and capped on the *world's* scale too: a cirque wider than the ice that cut it is a crater, and
    // one an appreciable fraction of the world across is a crater visible from orbit. See maxCirqueRadius.
    val radius = min(
      floorHalf * params.cirqueRadiusFactor,
      min(params.maxCirqueRadius, region.toWorld().width * MAX_CIRQUE_WORLD_SHARE)
    )
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
    metres: Double,
    minTroughLength: Double
  ): VectorFeature? {
    if (line.length < minTroughLength) return null

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

    /**
     * Whether the coarse carve can rasterise this feature by walking its outline.
     *
     * It can for anything whose influence radiates *outward* from its geometry - a corridor from its
     * centerline, a disc from its centre - because stamping a band `corridorWidthMax` wide along the
     * outline then covers everything the feature touches. It cannot for an [AreaFeature], whose influence
     * is on the *inside* of a closed curve: the band would paint the shore and skip the water.
     *
     * A named predicate rather than an inline `is` check, because the point of it is testable from outside
     * and "safe by construction" is a claim, not a guarantee. `AreaFeatureTest` calls this directly - a
     * subsystem that is never reached looks exactly like one that works, and this is that in reverse.
     */
    fun isRasterisable(feature: VectorFeature): Boolean = feature !is AreaFeature

    /**
     * Bounding-box cell count below which the coarse carve sweeps the box instead of tracing the geometry.
     *
     * A cirque's box is a handful of cells and tracing its outline ring would visit more of them than the box
     * contains. The crossover is not delicate - anything from a dozen to a few hundred behaves the same,
     * because what the trace exists to avoid is the *long thin diagonal* case, which is orders of magnitude
     * past this either way.
     */
    private const val COMPACT_FEATURE_CELLS = 64L

    private const val SMOOTHING = 2

    /** Station spacing along a trough centerline, in metres. The doc's figure. */
    private const val STATION_SPACING = 100.0

    /** Degrees below the snowline at which accumulation is at its maximum. */
    private const val COLD_SCALE = 12.0

    /** Annual precipitation at which accumulation is at its maximum, in millimetres. */
    private const val WET_SCALE = 1_400.0

    /**
     * Hard ceiling on a cirque's radius as a share of the world's width.
     *
     * A second cap behind [GlacialParams.maxCirqueRadius], for the case that one is raised: no landform of
     * this kind should ever be an appreciable fraction of a world, and a test world small enough that 1800 m
     * *is* an appreciable fraction should not get a crater either.
     */
    private const val MAX_CIRQUE_WORLD_SHARE = 0.02

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
