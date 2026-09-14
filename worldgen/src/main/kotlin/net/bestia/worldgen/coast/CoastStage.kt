package net.bestia.worldgen.coast

import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.climate.Winds
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.fields.ContourTrace
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.TectonicsStage
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.hydro.AlluviumStage
import net.bestia.worldgen.hydro.FlowRouting
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The shoreline, as a polyline with the shore's own attributes on it.
 *
 * `bio/BiomeStage` has carried the argument for this stage in a comment for as long as it has classified
 * beaches: *"a metres-wide feature being asked for on a kilometre grid, so what this really marks is the cell
 * the shoreline runs through"*. A kilometre classification can say a cell is coastal. It cannot say where the
 * water meets the land inside that cell, how wide the strand is there, or whether this particular headland is
 * sand or a cliff - and those are the three things a player standing on a beach is looking at.
 *
 * ### Traced against the surface a chunk will build, never against the raster
 *
 * The temptation is to contour `ELEVATION` at sea level on the kilometre grid. It is wrong by hundreds of
 * metres, and the measurement is already in this repository: `zone-server`'s own `Coastline` records that the
 * generator *"leaves a shelf two to four kilometres wide sitting within about twenty metres of sea level"*, so
 * the landward gradient at the shore is around a hundredth. `WorldHeightField` then adds a couple of metres of
 * detail noise on top, which on that gradient is two to three hundred metres of horizontal wander. So this
 * builds the finished heightfield and marches *that*, exactly as `hydro/PondStage` does for its own shoreline
 * and for the same stated reason: the surface a chunk will build is the only surface a shoreline may be found
 * against.
 *
 * ### It carries attributes and not height
 *
 * [MarkerFeature], so `affectsHeight` is false - the spelling `vector/VectorFeature` already anticipated for
 * *"coastline and trade-route annotations"*. A height feature would cost a per-column evaluation in every
 * coastal chunk in the world, and the beach wedge it would want is circular anyway: the waterline's position is
 * a function of the height being blended. What the chunk tier is missing is the *material* of the dry strand,
 * and material is what this supplies.
 *
 * ### Segments, and the claim that keeps them from overlapping
 *
 * A world's shore is hundreds of kilometres long, past `Ring.MAX_EXTENT` and far past
 * `AreaFeature.MAX_AREA_EXTENT`, so it cannot be one feature and is not an area in any case - the region it
 * would bound is "the continent". It is cut into segments that overlap geometrically by a few stations, so a
 * column near a junction still projects into some segment's interior, and each segment carries a **half-open
 * claim** on the loop's own arc length. The claims tile the loop exactly once, so exactly one segment answers
 * for any column - deterministically, with no gap and no double count.
 */
class CoastStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: CoastParams = CoastParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = params.digest().value

  /**
   * Six, and every one of them is a real read rather than an ordering trick.
   *
   * [GlacialStage] is the load-bearing one and the one that would "work" undeclared: `LayerStore` scopes a
   * stage to its transitive closure, so hydrology would drag elevation in anyway - and the stage would then be
   * tracing whichever surface happened to be current rather than the final one. That is the glacial lesson
   * exactly, one landform over: a fjord carved after the trace would leave the coastline inland of its own sea.
   */
  override val dependencies = listOf(
    AlluviumStage.ID,
    ClimateStage.ID,
    ErosionStage.ID,
    GlacialStage.ID,
    HydrologyStage.ID,
    TectonicsStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.COASTLINE))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val elevation = ctx.layers.float(LayerId.ELEVATION)
    val seaLevel = ctx.config.seaLevel
    val metres = region.resolution.metresPerCell

    val ocean = FlowRouting.oceanMask(Grid.from(elevation), seaLevel)
    if (ocean.none { it }) return StageResult.EMPTY

    // `query` and not `queryStrict`, for `PondStage`'s reason: this asks for the whole world, so the strict
    // form would trip on every stage that happens to sort before this one.
    val visible = ctx.features.query(region.toWorld())

    val base = WorldHeightField(
      elevation = elevation,
      hardness = ctx.layers.float(LayerId.ROCK_HARDNESS),
      seed = ctx.config.seed,
      seaLevel = seaLevel,
      params = params.detail
    )
    val evaluator = FeatureEvaluator(visible.filter { it.affectsHeight })

    fun groundAt(x: Double, y: Double) = evaluator.heightAt(x, y, base.heightAt(x, y))

    val field = SignField.build(region, ocean, params.cellSize, seaLevel, ::groundAt)
    if (field == null) return StageResult.EMPTY

    val contours = ContourTrace.trace(field.width, field.height, field.sea, field.values, field.valid)
    val fetch = Fetch(ocean, region.width, region.height, metres, params)

    val classifier = ShoreClassifier(
      params = params,
      seaLevel = seaLevel,
      hardness = ctx.layers.float(LayerId.ROCK_HARDNESS),
      sediment = ctx.layers.float(LayerId.SEDIMENT),
      temperature = ctx.layers.float(LayerId.TEMPERATURE),
      precipitation = ctx.layers.float(LayerId.PRECIPITATION),
      lakeId = ctx.layers.int(LayerId.LAKE_ID),
      deltas = visible.filter { it.kind == FeatureKind.DELTA || it.kind == FeatureKind.ALLUVIAL_FAN },
      fetch = fetch,
      windAt = { y -> Winds.directionAt(ClimateStage.latitudeOf(y / ctx.config.heightMetres)) },
      ground = ::groundAt
    )

    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>()

    // Longest first so a world's main landmass gets the low feature ids, which makes a probe or a viewer
    // listing read the same way twice. Ties broken on the first vertex, never on trace order.
    val ordered = contours
      .map { field.toWorld(it) }
      .filter { it.length >= params.minPerimeter }
      .sortedWith(compareByDescending<Polyline> { it.length }.thenBy { it.points[0].x }.thenBy { it.points[0].y })

    for (loop in ordered) {
      val line = refined(loop.resample(params.stationSpacing), ::groundAt, seaLevel)
      if (line.points.size < 4) continue

      out += segmentsOf(line, classifier, nextId)
    }

    return if (out.isEmpty()) StageResult.EMPTY else StageResult(features = out)
  }

  /**
   * Cuts one resampled loop into overlapping segments with tiling claims.
   *
   * [OVERLAP] stations of geometric overlap so a column near a junction still projects into the interior of the
   * segment that claims it - `Polyline.project` clamps at the ends, and a claim decided on a clamped projection
   * would be decided on the wrong station.
   */
  private fun segmentsOf(
    line: Polyline,
    classifier: ShoreClassifier,
    nextId: () -> net.bestia.worldgen.vector.FeatureId
  ): List<VectorFeature> {
    val points = line.points
    val out = ArrayList<VectorFeature>()

    var start = 0
    while (start < points.size - 1) {
      var end = min(points.size - 1, start + params.stationsPerSegment - 1)

      // The extent bound, which binds before the station count on a straight coast.
      while (end > start + 3 && spanOf(points, start, end) > params.maxSegmentExtent) {
        end--
      }

      val claimStart = if (start == 0) 0.0 else arcOf(points, start + OVERLAP)
      val last = end == points.size - 1
      val claimEnd = if (last) Double.MAX_VALUE else arcOf(points, end - OVERLAP)

      val lo = max(0, start - OVERLAP)
      val hi = min(points.size - 1, end + OVERLAP)
      val slice = Polyline(points.subList(lo, hi + 1))

      out += MarkerFeature(
        id = nextId(),
        kind = FeatureKind.COASTLINE,
        centerline = slice,
        stations = classifier.tableFor(slice, claimStart - arcOf(points, lo), claimEnd - arcOf(points, lo))
      )

      if (last) break
      start = end
    }

    return out
  }

  /**
   * Pulls every vertex onto the waterline, by bisecting the real surface across the line.
   *
   * The tracer can do no better than linear interpolation between two samples [CoastParams.cellSize] apart,
   * and the surface between them is not linear: the detail noise has a wavelength of a few hundred metres and
   * an amplitude of a couple, so a vertex can land a metre or two off the water even when the cell it came
   * from is right. On the coastal shelf's own gradient that is a hundred metres of horizontal error, which is
   * most of a beach.
   *
   * Cheap enough to be unconditional - a handful of evaluations per vertex, against a trace of a few thousand -
   * and it moves each vertex along the line's own normal, so a refined line keeps the shape the contour found
   * and only corrects where it sits.
   */
  private fun refined(line: Polyline, ground: (Double, Double) -> Double, seaLevel: Double): Polyline {
    val points = line.points
    val out = ArrayList<Vec2d>(points.size)

    for (i in points.indices) {
      val at = points[i]
      val before = points[max(0, i - 1)]
      val after = points[min(points.size - 1, i + 1)]

      val tx = after.x - before.x
      val ty = after.y - before.y
      val length = hypot(tx, ty)

      if (length == 0.0) {
        out.add(at)
        continue
      }

      val nx = -ty / length
      val ny = tx / length
      val reach = params.cellSize

      var lo = -reach
      var hi = reach
      val atLo = ground(at.x + nx * lo, at.y + ny * lo) - seaLevel
      val atHi = ground(at.x + nx * hi, at.y + ny * hi) - seaLevel

      // No crossing within a cell either side means the contour is already as close as this line can say, and
      // bisecting a function that does not change sign would only invent a position for it.
      if (atLo == 0.0 || atHi == 0.0 || (atLo < 0.0) == (atHi < 0.0)) {
        out.add(at)
        continue
      }

      var loNegative = atLo < 0.0
      repeat(REFINE_STEPS) {
        val mid = (lo + hi) * 0.5
        val value = ground(at.x + nx * mid, at.y + ny * mid) - seaLevel

        if ((value < 0.0) == loNegative) lo = mid else hi = mid
      }

      val t = (lo + hi) * 0.5
      out.add(Vec2d(at.x + nx * t, at.y + ny * t))
    }

    return Polyline(out)
  }

  private fun spanOf(points: List<Vec2d>, from: Int, to: Int): Double {
    var minX = Double.MAX_VALUE
    var minY = Double.MAX_VALUE
    var maxX = -Double.MAX_VALUE
    var maxY = -Double.MAX_VALUE

    for (i in from..to) {
      minX = min(minX, points[i].x)
      minY = min(minY, points[i].y)
      maxX = max(maxX, points[i].x)
      maxY = max(maxY, points[i].y)
    }

    return max(maxX - minX, maxY - minY)
  }

  /**
   * Arc length from the start of the line to a vertex, in metres.
   *
   * Clamped, because the callers ask for a vertex [OVERLAP] beyond a boundary and the last segment's boundary
   * is the end of the line - so the index legitimately runs past it and the answer is simply the whole length.
   */
  private fun arcOf(points: List<Vec2d>, index: Int): Double {
    var total = 0.0
    for (i in 1..index.coerceIn(0, points.size - 1)) {
      total += hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y)
    }
    return total
  }

  companion object {
    val ID = StageId("coast")

    /** Stations of geometric overlap either side of a claim. See [segmentsOf]. */
    private const val OVERLAP = 3

    /** Bisection steps per vertex in [refined]. Eight takes a 125 m bracket under half a metre. */
    private const val REFINE_STEPS = 8
  }
}
