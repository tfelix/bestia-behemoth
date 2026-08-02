package net.bestia.worldgen.hydro

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.AreaProfiles
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Tuning for [AlluviumStage]. */
data class AlluviumParams(

  /**
   * Channel gradient above which the river is confined, as a fall per metre travelled.
   *
   * A fan forms where flow stops being confined, so this and [fanOutletGradient] bracket the transition
   * rather than describing a place. One in fifty falling to one in a hundred and seventy is a stream
   * leaving a mountain front.
   *
   * **Fans are rare here and loosening the bracket barely changes that**, which is worth recording because
   * the obvious response to a thin count is to widen the threshold until it is not. Measured over twenty
   * 192-cell worlds:
   *
   * | confined / outlet | fans in all | worlds with none |
   * | --- | --- | --- |
   * | 0.040 / 0.010 | 10 | 13 |
   * | 0.020 / 0.006 | 15 | 9 |
   * | 0.012 / 0.004 | 14 | 10 |
   * | 0.008 / 0.003 | 14 | 9 |
   *
   * Halving the bracket three times moves the count by five, so the binding constraint is not the
   * threshold: it is that these centrelines are smoothed D8 flow paths, which descend steadily and rarely
   * present a *sharp* break of slope for a fan to form at. The same geometry is why oxbows are rare - see
   * `PondParams.oxbowTurn`. Deltas, which need only a mouth, come out in their hundreds.
   */
  val fanConfinedGradient: Double = 0.020,

  /** Gradient below which the flow is spreading, so the sediment it was carrying drops. */
  val fanOutletGradient: Double = 0.006,

  /** Length of a fan lobe as a multiple of the channel width at its apex. */
  val fanLengthFactor: Double = 26.0,

  /** Half-angle of a fan lobe, in radians. Wide, because that is what a fan is. */
  val fanSpread: Double = 0.85,

  /** Thickness of sediment at the apex of a fan, in metres. */
  val fanThickness: Double = 5.0,

  /** Length of a delta lobe as a multiple of the channel width at the mouth. */
  val deltaLengthFactor: Double = 34.0,

  /** Half-angle of a delta lobe, in radians. Narrower than a fan: the sea confines it from both sides. */
  val deltaSpread: Double = 0.7,

  /** Thickness of sediment at the head of a delta, in metres. */
  val deltaThickness: Double = 4.0,

  /**
   * Discharge below which a river builds nothing.
   *
   * The density control, and deliberately the hydrological one: a fan is built out of what the river
   * carries, so thinning by discharge thins by the thing that does the carrying rather than by a die roll.
   */
  val minDischarge: Double = 0.25,

  /** Shortest lobe worth emitting, in metres. */
  val minLength: Double = 90.0,

  /** Longest lobe, in metres. Well inside `AreaFeature.MAX_AREA_EXTENT`. */
  val maxLength: Double = 2_200.0
) : Params {

  init {
    require(fanConfinedGradient > fanOutletGradient) {
      "a fan forms where the gradient falls, so fanConfinedGradient must exceed fanOutletGradient"
    }
    require(fanOutletGradient > 0.0) { "fanOutletGradient must be positive, was $fanOutletGradient" }
    require(fanLengthFactor > 0.0) { "fanLengthFactor must be positive, was $fanLengthFactor" }
    require(fanSpread > 0.05 && fanSpread < PI * 0.9) { "fanSpread must be a half-angle, was $fanSpread" }
    require(fanThickness > 0.0) { "fanThickness must be positive, was $fanThickness" }
    require(deltaLengthFactor > 0.0) { "deltaLengthFactor must be positive, was $deltaLengthFactor" }
    require(deltaSpread > 0.05 && deltaSpread < PI * 0.9) { "deltaSpread must be a half-angle, was $deltaSpread" }
    require(deltaThickness > 0.0) { "deltaThickness must be positive, was $deltaThickness" }
    require(minDischarge >= 0.0) { "minDischarge must not be negative, was $minDischarge" }
    require(minLength > 0.0) { "minLength must be positive, was $minLength" }
    require(maxLength > minLength) { "maxLength must exceed minLength, was $maxLength" }
    require(maxLength <= AreaFeature.MAX_AREA_EXTENT) {
      "maxLength must fit an area feature's extent cap, was $maxLength"
    }
  }

  override fun digest() = ParamsDigest()
    .put("fanConfinedGradient", fanConfinedGradient)
    .put("fanOutletGradient", fanOutletGradient)
    .put("fanLengthFactor", fanLengthFactor)
    .put("fanSpread", fanSpread)
    .put("fanThickness", fanThickness)
    .put("deltaLengthFactor", deltaLengthFactor)
    .put("deltaSpread", deltaSpread)
    .put("deltaThickness", deltaThickness)
    .put("minDischarge", minDischarge)
    .put("minLength", minLength)
    .put("maxLength", maxLength)
}

/** Station channels on an [FeatureKind.ALLUVIAL_FAN] or a [FeatureKind.DELTA]. */
object AlluviumChannels {

  /** Metres of sediment this part of the lobe carries. Varies around the perimeter; see the profile. */
  const val THICKNESS = AreaProfiles.CHANNEL_THICKNESS

  /** Metres inward over which the wedge eases in from its own outline, so the toe has no step. */
  const val SHORE_REACH = AreaProfiles.CHANNEL_SHORE_REACH
}

/**
 * Where rivers put down what they were carrying: alluvial fans and deltas.
 *
 * ### Fed from the sediment budget rather than replacing it
 *
 * `ErosionStage` already models deposition, and it must keep doing so: hydrology re-routes flow over the
 * deposited elevation and `Stratigraphy` reads the sediment thickness, so removing that and putting fans
 * here instead would silently change where every river goes and what rock every column is made of. This is
 * the *sub-kilometre shape* of the same material - the lobe, its apex, its toe - which is exactly the thing
 * the raster tier cannot hold and the vector tier is for.
 *
 * ### The `ADD` blend, and the one path it must never reach
 *
 * A fan is piled on top of the terrain, so it blends [BlendMode.ADD]. That makes it the same hazard the
 * moraine already is: `GlacialStage.carveInto` rasterises features into the coarse elevation, and anything
 * applied both there and again at chunk time is built twice. It filters to `BlendMode.MIN`, so an additive
 * feature is excluded - and `AreaFeature` is excluded a second time by kind, because walking a ring's
 * outline would stamp its rim and miss its interior. Both guards are asserted rather than assumed;
 * see `GlacialStage.isRasterisable`.
 *
 * ### Two landforms, one shape
 *
 * A fan and a delta are the same wedge with different reasons. A fan forms where a confined channel reaches
 * a flat and the flow spreads; a delta forms where it reaches standing water and the flow stops. So the
 * detection differs - a gradient transition against a shoreline crossing - and the geometry does not.
 */
class AlluviumStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: AlluviumParams = AlluviumParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = params.digest().value

  /** Hydrology for the channels and the water level, erosion for the elevation the lobes sit on. */
  override val dependencies = listOf(ErosionStage.ID, HydrologyStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.ALLUVIAL_FAN),
    StageOutput.Vector(FeatureKind.DELTA)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val rivers = ctx.features.query(region.toWorld())
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      .filterIsInstance<PolylineFeature>()
    if (rivers.isEmpty()) return StageResult.EMPTY

    val discharge = ctx.layers.float(LayerId.DISCHARGE)
    val metres = region.resolution.metresPerCell
    val seaLevel = ctx.config.seaLevel

    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>()

    for (river in rivers.sortedBy { it.id.value }) {
      val line = river.centerline
      if (line.length < params.minLength * 2.0) continue

      val stations = river.stations
      val bed = runCatching { stations.channel(Profiles.CHANNEL_BED_ELEVATION) }.getOrNull() ?: continue
      val width = runCatching { stations.channel(Profiles.CHANNEL_WIDTH) }.getOrNull() ?: continue

      val mouth = line.pointAt(line.length)
      val cellX = Math.floor(mouth.x / metres).toInt()
      val cellY = Math.floor(mouth.y / metres).toInt()
      if (!region.contains(cellX, cellY)) continue
      if (discharge[cellX, cellY] < params.minDischarge) continue

      // A delta where the mouth is at the waterline. Checked first, because a river reaching the sea has
      // also reached a flat and would otherwise be read as a fan as well - and the sea is the better
      // explanation of the two.
      val mouthBed = stations.sample(bed, line.stationParamAt(line.length))
      if (mouthBed <= seaLevel + DELTA_TOLERANCE) {
        lobeAt(
          apex = mouth,
          bearing = line.tangentAt(line.length),
          channelWidth = stations.sample(width, line.stationParamAt(line.length)),
          kind = FeatureKind.DELTA,
          lengthFactor = params.deltaLengthFactor,
          spread = params.deltaSpread,
          thickness = params.deltaThickness,
          seed = river.id.value,
          nextId = nextId
        )?.let { out.add(it) }
        continue
      }

      // A fan where the channel stops being confined. Scanned from the mouth back, so the *lowest* such
      // transition on the river is the one that gets the fan - which is where the material ends up.
      var s = line.length - FAN_STEP
      while (s > FAN_STEP) {
        val below = gradientAt(stations, line, bed, s, +1)
        val above = gradientAt(stations, line, bed, s, -1)
        if (above >= params.fanConfinedGradient && below <= params.fanOutletGradient) {
          lobeAt(
            apex = line.pointAt(s),
            bearing = line.tangentAt(s),
            channelWidth = stations.sample(width, line.stationParamAt(s)),
            kind = FeatureKind.ALLUVIAL_FAN,
            lengthFactor = params.fanLengthFactor,
            spread = params.fanSpread,
            thickness = params.fanThickness,
            seed = river.id.value,
            nextId = nextId
          )?.let { out.add(it) }
          break
        }
        s -= FAN_STEP
      }
    }

    return StageResult(features = out)
  }

  /** Fall per metre over [FAN_STEP] on one side of [s]; positive downhill. */
  private fun gradientAt(
    stations: StationTable,
    line: net.bestia.worldgen.vector.Polyline,
    bed: Int,
    s: Double,
    direction: Int
  ): Double {
    val a = (s).coerceIn(0.0, line.length)
    val b = (s + direction * FAN_STEP).coerceIn(0.0, line.length)
    if (a == b) return 0.0
    val fall = stations.sample(bed, line.stationParamAt(min(a, b))) -
        stations.sample(bed, line.stationParamAt(max(a, b)))
    return fall / FAN_STEP
  }

  /**
   * One wedge of sediment, or null if it would be too small or too large to be one.
   *
   * The thickness channel is written *around the perimeter* rather than handed to the profile as a
   * constant, which is what makes the lobe thin from apex to toe with no apex coordinate in the profile at
   * all - see [AreaProfiles.fanWedge]. Station zero is the apex, because [Ring.fanLobe] puts it there.
   */
  private fun lobeAt(
    apex: Vec2d,
    bearing: Vec2d,
    channelWidth: Double,
    kind: FeatureKind,
    lengthFactor: Double,
    spread: Double,
    thickness: Double,
    seed: Long,
    nextId: () -> FeatureId
  ): AreaFeature? {
    if (channelWidth <= 0.0) return null
    val length = (channelWidth * lengthFactor).coerceIn(params.minLength, params.maxLength)

    val ring = runCatching {
      Ring.fanLobe(apex, bearing, length, spread, seed)
    }.getOrNull() ?: return null

    if (ring.bbox.width > AreaFeature.MAX_AREA_EXTENT) return null
    if (ring.bbox.height > AreaFeature.MAX_AREA_EXTENT) return null

    // Vertex 0 is the apex and the rest sweep the toe, so a taper in station index is a taper down the
    // lobe. Squared, because a fan's long profile is concave - most of the material is dropped early.
    val n = ring.vertexCount
    val table = StationTable.Builder(n, periodic = true)
      .channel(AlluviumChannels.THICKNESS) { station ->
        val along = station.toDouble() / n
        val taper = 1.0 - sqrt(along.coerceIn(0.0, 1.0))
        thickness * taper
      }
      .channel(AlluviumChannels.SHORE_REACH) { max(4.0, length * 0.12) }
      .build()

    return AreaFeature(
      id = nextId(),
      kind = kind,
      ring = ring,
      profile = AreaProfiles.fanWedge(table),
      perimeter = table,
      skirt = max(4.0, length * 0.06),
      blend = BlendMode.ADD
    )
  }

  companion object {
    val ID = StageId("alluvium")

    /** Metres between gradient samples along a channel. Two chunks; finer reads the smoothing, not the river. */
    private const val FAN_STEP = 60.0

    /**
     * Metres above sea level at which a river mouth counts as reaching the sea.
     *
     * Not zero: the bed elevation is a station value on a resampled centreline, and the last station is not
     * exactly at the waterline. A metre and a half is under one voxel of slack.
     */
    private const val DELTA_TOLERANCE = 1.5
  }
}
