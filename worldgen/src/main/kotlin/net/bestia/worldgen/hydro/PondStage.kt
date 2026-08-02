package net.bestia.worldgen.hydro

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
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
import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.vector.FeatureEvaluator
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.AreaProfiles
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.Quantize
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.Ring
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max
import kotlin.math.min

/** Tuning for [PondStage]. */
data class PondParams(

  /**
   * Metres the water stands below the lowest point of its own rim.
   *
   * A lake fills to its outlet, and this is how far short of overflowing it stops. It is also what turns
   * "no wall of water" from a threshold into a construction: every boundary sample is above the surface
   * because the surface is the minimum of exactly those samples, less this.
   *
   * Small, because a large value would drain every shallow pond out of existence - the rim of a valley
   * floor is not much above the floor.
   */
  val freeboard: Double = 1.5,

  /**
   * Deepest a pond may fill to above its own bottom, in metres.
   *
   * The ceiling on the bisection, and a real limit rather than a guard: a glacial trough is overdeepened by
   * up to ninety metres, so without one a single valley can come out as a fjord's worth of standing water
   * held in by nothing more than the fact that the search never looked further out.
   */
  val maxFill: Double = 30.0,

  /**
   * Shallowest a pond may be at its deepest point, in metres.
   *
   * The rule that keeps this from carpeting every valley floor with a film of water. A hollow whose lowest
   * point is a metre under its own rim is wet ground, not a lake, and drawing it as one would put a
   * shimmering sheet across half the mountains.
   */
  val minDepth: Double = 2.0,

  /**
   * Deepest the pond is cut below its own surface, in metres.
   *
   * The bowl this carves is shallow on purpose. The hollow is mostly *already there* - it is the
   * overdeepened trough floor the ice gouged, which `GlacialStage` has already carved into the raster - so
   * this only has to guarantee the last metre or two of basin, not dig the whole thing.
   */
  val maxDepth: Double = 4.5,

  /**
   * How far out the rim is sampled and the shore may reach, as a multiple of the trough's flat floor
   * half-width.
   *
   * **Greater than one, and that is not a margin.** A glacial trough is *flat* out to `half_width_floor` -
   * that flat floor is the diagnostic trait the whole feature exists to carve - so sampling the rim there
   * samples ground level with the bottom of the hollow, every candidate spills at any depth at all, and the
   * world comes out with no ponds. Which it did: at one floor width, eight of sixteen worlds had none and
   * the shore invariant passed by having nothing to check. Past the floor the wall climbs, and that is
   * where a rim is.
   *
   * Measured over sixteen 192-cell worlds, against pond count and against the shore invariant:
   *
   * | this | ponds in all | worlds with none | wall violations |
   * | --- | --- | --- | --- |
   * | 1.0 | 11 | 8 | 0 |
   * | 1.6 | 22 | 5 | 0 |
   * | 2.0 | 28 | 6 | 2 |
   * | 2.4 | 37 | 4 | 2 |
   *
   * Past 1.6 the extra ponds are bought by reaching so far up the wall that the shoreline march runs out of
   * room before it finds dry ground, which is the wall the invariant is there to catch. So: the most ponds
   * that come out clean.
   */
  val widthShare: Double = 1.6,

  /** Shortest a pond may be along the valley axis, in metres. Below this it is a puddle, not a feature. */
  val minLength: Double = 220.0,

  /**
   * Longest a pond may be along the valley axis, in metres.
   *
   * Well inside `AreaFeature.MAX_AREA_EXTENT`, and it needs to be: the trough floor can stay below the dam
   * crest for kilometres up a shallow valley, and a five-kilometre ribbon of standing water is a fjord, not
   * a tarn.
   */
  val maxLength: Double = 2_600.0,

  /** Metres of shore over which the pond's carve eases out, so its edge is a beach and not a kerb. */
  val shore: Double = 14.0,

  /** Vertices around the ring. Twenty-two samples of the valley axis is a metre or two of shore detail. */
  val vertices: Int = 22,

  // --- Oxbows -----------------------------------------------------------------------------------

  /**
   * Net turn a stretch of river must make before the bend counts as a meander loop, in radians.
   *
   * **Ninety degrees, not the half circle a real cut-off needs, and the reason is a finding rather than a
   * concession.** The plan for this producer assumed it would read a `Meander.offset` - there is no such
   * thing anywhere in `hydro/`. These centrelines are D8 flow paths, smoothed; they follow the steepest
   * descent of an eroded raster and never wander freely across a floodplain, so they simply do not double
   * back on themselves the way a lowland river does. Asking for a real loop asks for something this
   * pipeline does not produce.
   *
   * Measured over eight 192-cell worlds:
   *
   * | this | oxbows in all | worlds with none |
   * | --- | --- | --- |
   * | 3.4 (195 degrees) | 0 | 8 |
   * | 2.6 (150 degrees) | 1 | 7 |
   * | 2.0 (115 degrees) | 5 | 5 |
   * | 1.6 (92 degrees) | 8 | 4 |
   *
   * So an oxbow here is a *sharp bend the river has since straightened*, which is the honest description of
   * what the geometry supports, and it is still the shape that earned the vertex ring. Free meandering, and
   * with it the real thing, wants a lateral-migration pass on the channel that does not exist yet.
   */
  val oxbowTurn: Double = 1.6,

  /**
   * Share of meander loops that have been abandoned, in `[0,1]`.
   *
   * A thinning, and it has to be one: a river that had left an oxbow at every bend it ever made would
   * have a floodplain of nothing but oxbows. A third is generous and still reads as occasional.
   */
  val oxbowShare: Double = 0.34,

  /** How far outside the live channel an abandoned loop sits, as a multiple of the channel's width. */
  val oxbowOffset: Double = 2.2,

  /** Depth of an abandoned channel below the river's own water surface, in metres. */
  val oxbowDepth: Double = 2.4,

  /**
   * The chunk tier's detail noise, because this stage has to sample the surface a chunk will produce.
   *
   * Held for the same reason [TownParams][net.bestia.worldgen.civ.TownParams] holds it, and with the same
   * sharp edge: the shoreline is found by walking outward until the ground rises above the water, and if the
   * ground this stage walks is not the ground the chunk builds then the shoreline is in the wrong place.
   * Forwarded by `WorldParams.resolved` and deliberately not settable from a params file.
   */
  val detail: DetailParams = DetailParams()
) : Params {

  init {
    require(freeboard >= 0.0) { "freeboard must not be negative, was $freeboard" }
    require(minDepth > 0.0) { "minDepth must be positive, was $minDepth" }
    require(maxFill > minDepth) { "maxFill must exceed minDepth, was $maxFill" }
    require(maxDepth > 0.0) { "maxDepth must be positive, was $maxDepth" }
    require(widthShare > 0.0 && widthShare <= 3.0) { "widthShare must be in (0,3], was $widthShare" }
    require(minLength > 0.0) { "minLength must be positive, was $minLength" }
    require(maxLength > minLength) { "maxLength must exceed minLength, was $maxLength" }
    require(maxLength <= AreaFeature.MAX_AREA_EXTENT) {
      "maxLength must fit an area feature's extent cap of ${AreaFeature.MAX_AREA_EXTENT}, was $maxLength"
    }
    require(shore >= 0.0) { "shore must not be negative, was $shore" }
    require(vertices >= 6) { "vertices must be at least six, was $vertices" }
    require(oxbowTurn > 0.0) { "oxbowTurn must be positive, was $oxbowTurn" }
    require(oxbowShare in 0.0..1.0) { "oxbowShare must be in [0,1], was $oxbowShare" }
    require(oxbowOffset > 0.0) { "oxbowOffset must be positive, was $oxbowOffset" }
    require(oxbowDepth > 0.0) { "oxbowDepth must be positive, was $oxbowDepth" }
  }

  override fun digest() = ParamsDigest()
    .put("freeboard", freeboard)
    .put("minDepth", minDepth)
    .put("maxFill", maxFill)
    .put("maxDepth", maxDepth)
    .put("widthShare", widthShare)
    .put("minLength", minLength)
    .put("maxLength", maxLength)
    .put("shore", shore)
    .put("vertices", vertices)
    .put("oxbowTurn", oxbowTurn)
    .put("oxbowShare", oxbowShare)
    .put("oxbowOffset", oxbowOffset)
    .put("oxbowDepth", oxbowDepth)
    .nested("detail", detail.digest().value)
}

/** Station channels on a [FeatureKind.LAKE]. */
object LakeChannels {

  /** Elevation of the still water surface, in metres. Constant around the ring - a lake is level. */
  const val SURFACE_ELEVATION = Profiles.CHANNEL_SURFACE_ELEVATION

  /** Elevation of the deepest part of the bed, in metres. */
  const val FLOOR_ELEVATION = AreaProfiles.CHANNEL_FLOOR_ELEVATION

  /** How far the bed drops below the shore, in metres. */
  const val DEPTH = AreaProfiles.CHANNEL_DEPTH

  /** Metres inward over which the bed reaches full depth. */
  const val SHORE_REACH = AreaProfiles.CHANNEL_SHORE_REACH

  /** Shape of the bed's approach from shore to floor. */
  const val FLOOR_EXPONENT = AreaProfiles.CHANNEL_FLOOR_EXPONENT
}

/**
 * Moraine-dammed ponds: the standing water the raster tier cannot have.
 *
 * ### The one water body that is genuinely missing, and why it is this one
 *
 * A cirque tarn looks like the obvious first areal lake and is a trap. `GlacialStage` carves its cirques
 * into [LayerId.ELEVATION] with a `MIN` blend, so the bowl **is** in the raster, priority-flood in
 * [HydrologyStage] finds it, and there is already a lake there. A polygon over it would have to *remove*
 * raster water, and a feature cannot subtract.
 *
 * The pond behind a terminal moraine is different, and the difference is structural rather than a matter of
 * resolution. `GlacialStage.carveInto` filters the features it rasterises to `BlendMode.MIN`, precisely so
 * that the *additive* moraine is not built twice - once into the raster and once again by the chunk tier.
 * The consequence, which nobody wrote down until this stage needed it, is that **hydrology has never seen
 * the dam.** Priority-flood is looking at a surface with no moraine in it, so the depression behind the
 * moraine is not a depression, and the pond is unrecoverable from the raster at any resolution.
 *
 * So this is strictly additive to the world: every pond it emits is water that no other tier claims. That
 * is why [standsOnDryLand] gates each one on the raster being dry at its centroid - not as a safety net but
 * so that the count means something. A pond the raster already has would be a second opinion about the same
 * water, and two tiers with opinions about one lake is the failure this whole arrangement exists to avoid.
 *
 * ### Shape
 *
 * The pond follows the trough's own centerline through [Ring.ribbon] rather than being fitted to it. Reusing
 * the curve is what makes the pond and the valley agree about where the valley is; a warped circle placed at
 * the snout would sit across the ridge as often as along the floor. Its length is walked, not assumed: the
 * water backs up the valley until the trough's floor elevation rises above the surface, which is a real
 * answer that varies with how overdeepened the floor is.
 *
 * ### What the chunk tier does with it
 *
 * The `MIN`-blended bowl gives the bed - shallow, because the hollow is mostly the ice's work already - and
 * `voxel/PondWater.kt` reads [LakeChannels.SURFACE_ELEVATION] to fill it, as a third water surface beside
 * the raster's and the rivers'. No biome change is needed: `SurfaceCover.cap` already returns a lake bed
 * once `waterDepth > 0`, so the shore, the bed material and the ice cover in a cold climate all follow.
 */
class PondStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: PondParams = PondParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = params.digest().value

  /**
   * Glacial for the troughs and the moraines, hydrology for the rivers and the water the raster already has.
   *
   * Erosion is not here even though the pond reads elevation, because what it actually reads is
   * [LayerId.ELEVATION], and glacial is the stage that writes it last.
   *
   * [AlluviumStage] is, and it is a real read rather than an ordering trick: the surface this stage walks
   * to find a rim is the finished heightfield, and a fan is part of it. A sediment lobe across a valley
   * floor is a dam like any other, so a pond whose rim search could not see one would be filled to the
   * wrong level - or filled at all where the fan has since buried the hollow.
   */
  override val dependencies = listOf(AlluviumStage.ID, GlacialStage.ID, HydrologyStage.ID)
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.LAKE),
    StageOutput.Vector(FeatureKind.OXBOW_LAKE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val world = region.toWorld()
    // `query`, not `queryStrict`. The strict form returns everything the index holds and throws if any of
    // it came from an undeclared stage, which is right for a narrow query where a surprise means a bug and
    // wrong for a world-wide one - this asks for the whole region, so it would trip on every stage that
    // happens to sort before this one. The filtering form gives exactly the declared dependencies' output,
    // which is what the ground evaluator below should be built from.
    val visible = ctx.features.query(world)

    val troughs = visible
      .filter { it.kind == FeatureKind.GLACIAL_TROUGH }
      .filterIsInstance<PolylineFeature>()

    // A trough only dams if a moraine was actually emitted at its snout. Glacial drops moraines whose ridge
    // polyline is degenerate, so this is not a formality - checking the trough alone would put a pond behind
    // a dam that does not exist.
    val moraines = visible.filter { it.kind == FeatureKind.MORAINE }

    val rivers = visible
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      .filterIsInstance<PolylineFeature>()

    if ((troughs.isEmpty() || moraines.isEmpty()) && rivers.isEmpty()) return StageResult.EMPTY

    val elevation = ctx.layers.float(LayerId.ELEVATION)
    val waterLevel = ctx.layers.float(LayerId.WATER_LEVEL)
    val metres = region.resolution.metresPerCell

    // The surface a chunk will build, which is the only surface the shoreline may be found against. See
    // `shorelineHalfWidth`. Built exactly as `civ/TownStage.kt`'s `WorldGround` builds its own, and for the
    // same reason - `assemble` has not run, so `GeneratedWorld.base` does not exist yet.
    val base: BaseHeightField = WorldHeightField(
      elevation = elevation,
      hardness = ctx.layers.float(LayerId.ROCK_HARDNESS),
      seed = ctx.config.seed,
      seaLevel = ctx.config.seaLevel,
      params = params.detail
    )

    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>()

    // Longest trough first, so where a tributary shares a snout with its trunk the trunk's pond is the one
    // that survives the overlap test below. Ordering by length rather than by id keeps that deterministic
    // without making it depend on which trough happened to be emitted first.
    val ordered = troughs.sortedWith(compareByDescending<PolylineFeature> { it.centerline.length }
      .thenBy { it.id.value })

    val ponds = ArrayList<AreaFeature>()
    for (trough in ordered) {
      val snout = trough.centerline.pointAt(trough.centerline.length)
      if (moraines.none { it.bbox.contains(snout.x, snout.y) }) continue

      val pond = pondBehind(
        trough, region, elevation, waterLevel, base, visible, metres, ctx.config.seaLevel, nextId
      ) ?: continue

      // Tributary troughs end at the same snout as their trunk, so without this a confluence emits three or
      // four near-identical ponds stacked on one another - measured at up to five on the reference world.
      // They are not merged, because two overlapping rings with different surfaces would each claim their own
      // water level over the shared ground; the later one is simply dropped.
      if (ponds.any { it.contains(pond.ring.centroid.x, pond.ring.centroid.y) }) continue
      ponds.add(pond)
    }

    // Oxbows, from the same stage because they are the same thing one tier down: standing water that no
    // raster holds. Sharing the stage shares the overlap test as well, so an abandoned meander cannot be
    // emitted on top of a tarn, and costs one version number instead of two.
    // A salt rather than a stream: which loops are abandoned must be a pure function of the world, not of
    // the order the walk reached them, so every draw is a hash of the river and the loop's own arc length.
    val oxbowSalt = GenRng.hash(ctx.seed, id.hash, version.toLong(), OXBOW_SALT)
    for (river in rivers.sortedBy { it.id.value }) {
      for (oxbow in oxbowsBeside(river, base, visible, oxbowSalt, nextId)) {
        if (ponds.any { it.contains(oxbow.ring.centroid.x, oxbow.ring.centroid.y) }) continue
        ponds.add(oxbow)
      }
    }

    out.addAll(ponds)

    return StageResult(features = out)
  }

  /**
   * One pond, or null for any of the several perfectly ordinary reasons there is not one.
   *
   * Reads the trough's own station table for the floor elevation and half-width, so the pond is described in
   * the same terms the valley is. Everything that can fail returns null rather than throwing: a world with a
   * short trough, a trough whose floor climbs immediately, or a valley that bends tighter than the pond is
   * wide is a normal world, not a broken one.
   */
  private fun pondBehind(
    trough: PolylineFeature,
    region: CellRegion,
    elevation: FloatLayer,
    waterLevel: FloatLayer,
    base: BaseHeightField,
    visible: List<VectorFeature>,
    metres: Double,
    seaLevel: Double,
    nextId: () -> FeatureId
  ): AreaFeature? {
    val line = trough.centerline
    val stations = trough.stations

    val halfFloorChannel = runCatching { stations.channel(Profiles.CHANNEL_HALF_WIDTH_FLOOR) }.getOrNull()
      ?: return null

    // One evaluator for this pond's neighbourhood, built the way `ChunkHeightSampler` builds one per chunk.
    // Narrowed to the trough's own bounds so each sample is a loop over a handful of features rather than
    // over every trough, cirque and river in the world.
    //
    // It includes the **moraine**, which is the whole reason the dam does not have to be modelled here: the
    // ridge is an `ADD`-blend feature and this is the surface with every feature stamped, so the dam is
    // simply part of the ground the rim search walks over.
    val ground = FeatureEvaluator(
      visible.filter { it.bbox.intersects(trough.bbox.expanded(MARCH_MARGIN)) }
    )

    fun groundAt(at: Vec2d) = ground.heightAt(at.x, at.y, base.heightAt(at.x, at.y))

    /*
     * Surface and extent, found by filling the hollow until it spills.
     *
     * Three attempts got here. The first two derived the surface from the dam - "the water backs up to some
     * fraction of the moraine's height" - and both left walls of standing water twenty to seventy metres
     * tall, which the shore invariant caught. The reason is structural rather than a matter of tuning: this
     * is deriving a *basin* from a *corridor*, and a trough's cross-section does not describe one. It is
     * overdeepened by up to ninety metres along its length, its stated wall height is a property of the
     * carve rather than of the surrounding land, and a `MIN` blend makes even that only an upper bound on
     * the real ground.
     *
     * The third attempt read the surface off the rim in one shot and produced **no ponds at all** - the
     * invariant passed by having nothing to check, which is the failure this module has shipped three times
     * and the reason the sweep prints a count. It walked back from the dam, and the ground at the dam is the
     * moraine crest, so it stopped on its first step every time.
     *
     * What is here now is the thing both of those were approximating: fill from the lowest point of the
     * valley floor and stop just before the water finds a way out. `spills` is monotone in the level - a
     * higher lake has a longer shoreline and therefore a lower rim - so the largest level that does not
     * spill is a bisection. No wall is possible by construction, because at the level chosen every boundary
     * sample is above the water; that is what not spilling means.
     */
    val step = max(metres * 0.05, WALK_STEP)
    val searchFrom = max(0.0, line.length - params.maxLength)

    // The bottom of the hollow: where the water starts from.
    var bottom = Double.MAX_VALUE
    var bottomAt = line.length
    run {
      var s = searchFrom
      while (s <= line.length) {
        val here = groundAt(line.pointAt(s))
        if (here < bottom) {
          bottom = here
          bottomAt = s
        }
        s += step
      }
    }
    if (bottom == Double.MAX_VALUE) return null

    /** The wet interval of the axis containing the bottom, at a candidate water level. */
    fun extentAt(level: Double): Pair<Double, Double> {
      var lo = bottomAt
      while (lo - step >= searchFrom && groundAt(line.pointAt(lo - step)) < level) lo -= step
      var hi = bottomAt
      while (hi + step <= line.length && groundAt(line.pointAt(hi + step)) < level) hi += step
      return lo to hi
    }

    /** Whether water at [level] would find its way out over the boundary that encloses it. */
    fun spills(level: Double): Boolean {
      val (lo, hi) = extentAt(level)
      if (hi - lo > params.maxLength) return true
      for (i in 0..RIM_SAMPLES) {
        val s = lo + (hi - lo) * i / RIM_SAMPLES
        val at = line.pointAt(s)
        val normal = line.tangentAt(s).perpendicular()
        val half = stations.sample(halfFloorChannel, line.stationParamAt(s)) * params.widthShare
        for (side in intArrayOf(1, -1)) {
          // Two distances, not one. A rim sampled at a single radius is a rim one sample thick: the ground
          // is above the water exactly there and free to dip again immediately outside, which is a wall the
          // pond's own boundary cannot see. Measured on a 200-seed sweep at 256 cells, eight ponds in six
          // hundred had one, the worst nineteen metres tall.
          for (out in doubleArrayOf(half, half + RIM_THICKNESS)) {
            if (groundAt(Vec2d(at.x + normal.x * out * side, at.y + normal.y * out * side)) < level) return true
          }
        }
      }
      return false
    }

    // The shallowest pond worth having must already hold; if it does not, this hollow is not a basin.
    val floorLevel = bottom + params.minDepth
    if (spills(floorLevel)) return null

    var low = floorLevel
    var high = bottom + params.maxFill
    repeat(FILL_STEPS) {
      val mid = (low + high) * 0.5
      if (spills(mid)) high = mid else low = mid
    }
    val surface = low - params.freeboard
    if (surface - bottom < params.minDepth) return null

    val (from, to) = extentAt(surface)
    val length = to - from
    if (length < params.minLength) return null
    val deepest = bottom

    val spine = runCatching { subLine(trough, from, to) }.getOrNull() ?: return null

    val ring = runCatching {
      Ring.ribbon(spine, params.vertices) { t, side ->
        val s = from + t * length
        shorelineHalfWidth(
          at = line.pointAt(s),
          outward = line.tangentAt(s).perpendicular() * side.toDouble(),
          limit = stations.sample(halfFloorChannel, line.stationParamAt(s)) * params.widthShare,
          groundAt = ::groundAt,
          surface = surface
        )
      }
    }.getOrNull() ?: return null

    if (ring.bbox.width > AreaFeature.MAX_AREA_EXTENT) return null
    if (ring.bbox.height > AreaFeature.MAX_AREA_EXTENT) return null
    if (!standsOnDryLand(ring.centroid, region, elevation, waterLevel, metres, seaLevel, surface)) return null
    if (wouldLeaveAWall(ring, surface, ::groundAt)) return null

    val depth = min(params.maxDepth, surface - deepest)
    val table = StationTable.Builder(ring.vertexCount, periodic = true)
      .channel(LakeChannels.SURFACE_ELEVATION) { surface }
      .channel(LakeChannels.FLOOR_ELEVATION) { surface - depth }
      .channel(LakeChannels.DEPTH) { depth }
      .channel(LakeChannels.SHORE_REACH) { params.shore }
      .channel(LakeChannels.FLOOR_EXPONENT) { 1.5 }
      .build()

    return AreaFeature(
      id = nextId(),
      kind = FeatureKind.LAKE,
      ring = ring,
      profile = AreaProfiles.bowl(table),
      perimeter = table,
      skirt = params.shore,
      blend = BlendMode.MIN
    )
  }

  /**
   * The abandoned meander loops beside one river.
   *
   * ### Why the oxbow is drawn *beside* the channel rather than along it
   *
   * A real oxbow is the old course, left behind when the river cut through the neck of a loop. This
   * pipeline's rivers never cut through - `RIVER_CHANNEL` still follows every bend it ever made - so
   * drawing the lake on the loop would put standing water in the live channel and dam the river with its
   * own history. Offsetting the loop outward by a couple of channel widths puts it where an abandoned
   * course actually lies: on the floodplain, outside the bend, parallel to a river that has since moved in.
   *
   * ### And why this is the shape that earned the vertex ring
   *
   * A crescent is not star-shaped about any interior point, so no radial `r(theta)` can express one - some
   * ray from the centre crosses its boundary four times. The moraine ponds could all have been written with
   * a radial type; this one could not, which is what [Ring]'s KDoc means when it says the oxbow is the case
   * that decided the geometry. Here the crescent comes out of [Ring.ribbon] following the loop's own arc
   * rather than out of [Ring.crescent], because the arc is a curve hydrology already computed and refitting
   * a lune to it would be a second opinion about where the bend is.
   *
   * ### Why it needs no rim search
   *
   * Unlike a tarn, an oxbow **carves its own basin**: it is a `MIN` bowl cut two metres into a floodplain
   * that is already above the river's surface, so the ground at the ring boundary is above the water by
   * construction and there is nothing for the shore invariant to catch. The tarn has no such luxury,
   * because it is trying to hold water in a hollow somebody else shaped.
   */
  private fun oxbowsBeside(
    river: PolylineFeature,
    base: BaseHeightField,
    visible: List<VectorFeature>,
    oxbowSalt: Long,
    nextId: () -> FeatureId
  ): List<AreaFeature> {
    val line = river.centerline
    if (line.length < OXBOW_MIN_LOOP) return emptyList()

    val stations = river.stations
    val bedChannel = runCatching { stations.channel(Profiles.CHANNEL_BED_ELEVATION) }.getOrNull()
      ?: return emptyList()
    val widthChannel = runCatching { stations.channel(Profiles.CHANNEL_WIDTH) }.getOrNull()
      ?: return emptyList()
    val depthChannel = runCatching { stations.channel(Profiles.CHANNEL_DEPTH) }.getOrNull()
      ?: return emptyList()

    val ground = FeatureEvaluator(visible.filter { it.bbox.intersects(river.bbox.expanded(MARCH_MARGIN)) })
    val out = ArrayList<AreaFeature>()

    // Walk the line accumulating signed turn. A loop is a stretch that turns through `oxbowTurn` without
    // changing its mind - the sign matters, because a river that turns left and then right by the same
    // amount has made an S, not a loop, and an unsigned sum would call it one.
    var start = 0.0
    var turn = 0.0
    var s = OXBOW_STEP
    var previous = line.tangentAt(0.0)

    while (s <= line.length) {
      val tangent = line.tangentAt(s)
      // Net turn, accumulated with its sign and never reset on a wobble. Resetting on each sign change was
      // the first attempt and it found no loops at all on any world: these centrelines are smoothed D8
      // paths, so the bearing jitters either way at every step and an accumulator that restarts on a
      // reversal never reaches half a circle. Signed accumulation lets the jitter cancel, which is the
      // whole reason to use the signed form - an unsigned sum would call an S-bend a loop.
      turn += Math.atan2(previous cross tangent, previous dot tangent)
      previous = tangent

      // A stretch that has wandered a long way without committing to a direction is not the beginning of a
      // loop, so the origin is dragged forward rather than left behind to poison the next one.
      if (Math.abs(turn) < OXBOW_DRIFT && s - start > OXBOW_MIN_LOOP * 3.0) {
        start = s
        turn = 0.0
      }

      if (Math.abs(turn) >= params.oxbowTurn && s - start >= OXBOW_MIN_LOOP) {
        val loop = oxbowOn(
          river, start, s, Math.signum(turn), stations,
          bedChannel, widthChannel, depthChannel, base, ground, oxbowSalt, nextId
        )
        if (loop != null) out.add(loop)
        // Skip past the loop either way, so one long bend is one oxbow rather than a chain of overlapping
        // ones sliding along it.
        start = s
        turn = 0.0
      }

      s += OXBOW_STEP
    }

    return out
  }

  /** One abandoned loop, offset to the outside of the bend it was left by. */
  private fun oxbowOn(
    river: PolylineFeature,
    from: Double,
    to: Double,
    handedness: Double,
    stations: StationTable,
    bedChannel: Int,
    widthChannel: Int,
    depthChannel: Int,
    base: BaseHeightField,
    ground: FeatureEvaluator,
    oxbowSalt: Long,
    nextId: () -> FeatureId
  ): AreaFeature? {
    val line = river.centerline

    // Thinned per loop, keyed on the river and the loop's own arc length so the decision is a pure function
    // of the world rather than of the order the walk happened to reach it.
    if (GenRng.hashUnit(oxbowSalt, river.id.value, Quantize.toFixed(from)) > params.oxbowShare) return null

    val midU = line.stationParamAt((from + to) * 0.5)
    val width = stations.sample(widthChannel, midU)
    val depth = stations.sample(depthChannel, midU)
    if (width <= 0.0) return null

    val bed = stations.sample(bedChannel, midU)
    // The river's own water surface, by the same convention `RiverWaterSampler` fills to.
    val surface = bed - depth * 0.25

    // Outward is away from the inside of the bend: the loop turned one way, so the abandoned course lies on
    // the other side of it.
    val offset = width * params.oxbowOffset * -handedness
    val spinePoints = (0..OXBOW_SPINE).map { i ->
      val at = from + (to - from) * i / OXBOW_SPINE
      val point = line.pointAt(at)
      val normal = line.tangentAt(at).perpendicular()
      Vec2d(point.x + normal.x * offset, point.y + normal.y * offset)
    }
    val spine = runCatching { Polyline(spinePoints) }.getOrNull() ?: return null

    val ring = runCatching {
      Ring.ribbon(spine, params.vertices) { t, _ ->
        // Tapered to nothing at both ends, because an abandoned channel silts up from its cut ends inward -
        // which is also what closes the ribbon into a crescent rather than a sausage.
        width * 0.5 * Math.sin(t * Math.PI)
      }
    }.getOrNull() ?: return null

    if (ring.bbox.width > AreaFeature.MAX_AREA_EXTENT) return null
    if (ring.bbox.height > AreaFeature.MAX_AREA_EXTENT) return null
    if (ring.area < OXBOW_MIN_AREA) return null

    // The floodplain here has to be above the water, or this is not a floodplain - it is the channel, or a
    // lake, or the sea, and an oxbow in any of them is water on water.
    val here = ground.heightAt(ring.centroid.x, ring.centroid.y, base.heightAt(ring.centroid.x, ring.centroid.y))
    if (here <= surface) return null

    val table = StationTable.Builder(ring.vertexCount, periodic = true)
      .channel(LakeChannels.SURFACE_ELEVATION) { surface }
      .channel(LakeChannels.FLOOR_ELEVATION) { surface - params.oxbowDepth }
      .channel(LakeChannels.DEPTH) { params.oxbowDepth }
      .channel(LakeChannels.SHORE_REACH) { max(2.0, width * 0.25) }
      .channel(LakeChannels.FLOOR_EXPONENT) { 1.4 }
      .build()

    return AreaFeature(
      id = nextId(),
      kind = FeatureKind.OXBOW_LAKE,
      ring = ring,
      profile = AreaProfiles.bowl(table),
      perimeter = table,
      skirt = max(2.0, width * 0.35),
      blend = BlendMode.MIN
    )
  }

  /**
   * How far the water reaches from the spine on one side, in metres: the shoreline, found by walking to it.
   *
   * Walked against the finished heightfield rather than solved against the trough's cross-section, because
   * the trough is stamped with a `MIN` blend and its profile is therefore only an *upper bound* on the
   * ground. Where the natural terrain already lies below the trough wall the water reaches further than the
   * profile says, and a ring sized from the profile leaves standing water with dry land beside it.
   *
   * Bounded by [limit], which is the same half-width the rim search used - so the shoreline can never be
   * outside the boundary the surface was defined against, and the pond cannot escape its own rim.
   *
   * It stops at the *first* dry column deliberately: a hollow further up the hillside is not part of this
   * lake, however low it is.
   */
  private fun shorelineHalfWidth(
    at: Vec2d,
    outward: Vec2d,
    limit: Double,
    groundAt: (Vec2d) -> Double,
    surface: Double
  ): Double {
    var distance = 0.0
    while (distance + MARCH_STEP <= limit) {
      val next = distance + MARCH_STEP
      val here = groundAt(Vec2d(at.x + outward.x * next, at.y + outward.y * next))
      // Dry, *and still dry a little further out*. One dry sample is not a shore - the ground can rise
      // above the water for a few metres and dip straight back under it, and stopping there leaves the ring
      // boundary with a hollow of standing water just outside. Thickening the rim search did nothing for
      // this, because the boundary that fails is the one the march chose, not the one the rim checked.
      if (here >= surface) {
        val beyond = distance + MARCH_STEP + RIM_THICKNESS
        if (groundAt(Vec2d(at.x + outward.x * beyond, at.y + outward.y * beyond)) >= surface) break
      }
      distance = next
    }
    // Half a step past the last wet sample, onto ground already above the surface, and never past the rim.
    return min(distance + MARCH_STEP * 0.5, limit)
  }

  /**
   * Whether this ring would leave standing water with dry ground beside it, checked the way the invariant
   * checks it.
   *
   * The producer running the checker's own test, and it is here because the alternative was arguing about a
   * tolerance. A ribbon along an axis cannot exactly represent a lake's plan shape - near the ends the real
   * shoreline curves round, and a perpendicular march has no way to follow it - so a small share of ponds
   * come out with a boundary the water overruns. Three attempts to fix that geometrically all failed, and
   * the third made it much worse: tapering the caps pulled the ring *inside* the waterline, which is the one
   * thing guaranteed to cause the very fault it was meant to remove. Shrinking a ring never helps.
   *
   * So the pond is dropped instead. On a 200-seed sweep at 256 cells this refuses about one candidate in
   * eighty, and it turns "the shore invariant holds to within four metres" into "the shore invariant holds",
   * which is worth more than the ponds.
   *
   * Deliberately stricter than `Invariants.MAX_SHORE_WALL`, so the two are not the same number arrived at
   * twice and a change to one does not silently make the other vacuous.
   */
  private fun wouldLeaveAWall(
    ring: Ring,
    surface: Double,
    groundAt: (Vec2d) -> Double
  ): Boolean {
    for (i in 0 until ring.vertexCount) {
      val vertex = ring.vertex(i)
      val before = outwardOf(ring.vertex(i - 1), vertex)
      val after = outwardOf(vertex, ring.vertex(i + 1))
      val outward = (before + after).normalized()
      if (outward.lengthSquared == 0.0) continue
      val outside = Vec2d(vertex.x + outward.x * WALL_PROBE, vertex.y + outward.y * WALL_PROBE)
      if (ring.contains(outside)) continue
      if (surface - groundAt(outside) > MAX_WALL) return true
    }
    return false
  }

  /** Outward normal of a counter-clockwise ring edge: its tangent turned clockwise. */
  private fun outwardOf(from: Vec2d, to: Vec2d): Vec2d {
    val edge = (to - from).normalized()
    return Vec2d(edge.y, -edge.x)
  }

  /**
   * Whether this is somewhere a pond can be: on land, dry in the raster, and not floating above the ground.
   *
   * Three refusals, and they are three because measuring showed one of them was quietly doing another's job.
   * The tier-disagreement guard alone rejected most trough snouts on a 192-cell world with discrepancies of
   * a hundred to nearly three hundred metres - which is not tier disagreement at all, it is a **glacial
   * trough ending at the coast**, where the trough's floor station is a little above sea level and the
   * coarse cell under it is the ocean floor. Right answer, wrong reason, and a threshold quietly carrying a
   * case it was not sized for is how a threshold ends up being tuned against the wrong evidence.
   */
  private fun standsOnDryLand(
    centroid: Vec2d,
    region: CellRegion,
    elevation: FloatLayer,
    waterLevel: FloatLayer,
    metres: Double,
    seaLevel: Double,
    surface: Double
  ): Boolean {
    val cellX = Math.floor(centroid.x / metres).toInt()
    val cellY = Math.floor(centroid.y / metres).toInt()
    if (!region.contains(cellX, cellY)) return false

    // A trough that runs out into the sea. Its moraine is a submarine bar and whatever is behind it is the
    // sea, not a tarn. WATER_LEVEL is a *lake* layer and is NaN over open ocean, so this is not covered by
    // the check below it.
    if (elevation[cellX, cellY] < seaLevel || surface <= seaLevel) return false

    // Water the raster already has. The gate that makes the pond census mean "ponds the raster does not
    // have" rather than "ponds", so the two tiers can never hold opinions about the same lake.
    if (!waterLevel[cellX, cellY].isNaN()) return false

    // Genuine tier disagreement: the trough is a vector carve the raster holds only bicubically smeared, so
    // a pond in a narrow valley legitimately stands a few metres above the kilometre cell's average. Much
    // beyond that and the two tiers disagree about where the ground is, and the pond is dropped.
    return surface < elevation[cellX, cellY] + MAX_ABOVE_RASTER
  }

  /** The last [length] metres of a trough's centerline, resampled for the ribbon to walk. */
  private fun subLine(trough: PolylineFeature, from: Double, to: Double) = Polyline(
    (0..SPINE_SAMPLES).map { trough.centerline.pointAt(from + (to - from) * it / SPINE_SAMPLES) }
  )

  companion object {
    val ID = StageId("pond")

    /**
     * Metres the water may stand above the coarse elevation at the pond's centroid before it is refused.
     *
     * Not zero, and it must not be: the trough's floor is a *vector* carve that the raster only holds
     * bicubically smeared, so a pond in a narrow valley legitimately sits a few metres above the kilometre
     * cell's average. Beyond this the two tiers genuinely disagree and the pond is dropped.
     */
    private const val MAX_ABOVE_RASTER = 30.0

    /**
     * Samples of the rim, per axis, when finding the level the water fills to.
     *
     * Twelve stations along a pond of at most 2.6 km is a sample every couple of hundred metres on each
     * flank. Finer than that buys nothing: the rim is being used to find a *minimum*, and a low saddle
     * narrower than two hundred metres is a stream cutting through the moraine rather than an outlet the
     * lake level should be set by.
     */
    private const val RIM_SAMPLES = 12

    /**
     * Metres of ground beyond the boundary that must also be above the water, for the rim to count.
     *
     * Wider than `Invariants.SHORE_PROBE`, deliberately: the invariant checks a band outside the ring, so
     * the producer has to guarantee at least that band. Setting it from the checker rather than the other
     * way round is what keeps the two from drifting into an argument about a threshold.
     */
    private const val RIM_THICKNESS = 16.0

    /** Metres outside the ring the producer checks for a wall. Matches `Invariants.SHORE_PROBE`. */
    private const val WALL_PROBE = 14.0

    /** Metres of wall the producer tolerates before dropping a pond. Tighter than the invariant's bound. */
    private const val MAX_WALL = 3.0


    /**
     * Bisection steps for the water level.
     *
     * Ten halvings of a thirty-metre range settle the level to three centimetres, which is far finer than
     * the metre-scale voxels it will be rendered at and than the eight-metre spacing the rim is sampled on.
     */
    private const val FILL_STEPS = 10

    /** Salt for the per-loop abandonment draw, so it cannot collide with any other stream in the stage. */
    private const val OXBOW_SALT = 0x0B_0000_5A17L

    /** Metres between samples when walking a river looking for a meander loop. */
    private const val OXBOW_STEP = 60.0

    /** Shortest stretch of river that can be a loop, in metres. Below it a tight kink is not a meander. */
    private const val OXBOW_MIN_LOOP = 400.0

    /** Net turn below which a long stretch counts as wandering rather than as the start of a loop. */
    private const val OXBOW_DRIFT = 0.35

    /** Samples of the loop arc handed to the ribbon. */
    private const val OXBOW_SPINE = 14

    /** Square metres below which an abandoned channel is a puddle in a field. */
    private const val OXBOW_MIN_AREA = 900.0

    /** Metres between floor samples when walking the water back up the valley. */
    private const val WALK_STEP = 40.0

    /**
     * Metres between samples when walking outward to the shore.
     *
     * Also the granularity of the shoreline, and therefore of the small step the overshoot has to cover.
     * Eight metres is a quarter of a chunk and about four times the detail noise's amplitude, so the
     * shoreline lands within a step of the true one and the overshoot puts the ring past it either way.
     */
    private const val MARCH_STEP = 8.0

    /** Metres of slack when narrowing the feature set the march evaluates. Wider than any pond's reach. */
    private const val MARCH_MARGIN = 500.0

    /** Samples of the trough centerline handed to the ribbon. */
    private const val SPINE_SAMPLES = 12

  }
}
