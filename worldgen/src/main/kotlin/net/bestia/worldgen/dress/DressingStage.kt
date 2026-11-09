package net.bestia.worldgen.dress

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.BuildingChannels
import net.bestia.worldgen.civ.BuildingFunction
import net.bestia.worldgen.civ.FieldChannels
import net.bestia.worldgen.civ.RoadClass
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.civ.SettlementTier
import net.bestia.worldgen.civ.TownStage
import net.bestia.worldgen.coast.CoastChannels
import net.bestia.worldgen.coast.CoastStage
import net.bestia.worldgen.coast.ShoreKind
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.Chronicle
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.IntLayer
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.geo.KnollStage
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.LakeChannels
import net.bestia.worldgen.hydro.PondStage
import net.bestia.worldgen.pop.EconomyStage
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.AreaFeature
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuning for [DressingStage]. */
data class DressingParams(

  /** Metres between lanterns along a road out of a settlement, before jitter. */
  val lanternSpacing: Double = 52.0,

  /** Range of how far out of a settlement its lanterns reach, in metres. Drawn per settlement. */
  val lanternReachMin: Double = 2_000.0,
  val lanternReachMax: Double = 3_000.0,

  /** Metres between milestones on a trunk road. */
  val milestoneSpacing: Double = 2_000.0,

  /** Metres a trail must fall away on both sides of a crest for the crest to get a cairn. */
  val cairnRelief: Double = 40.0,

  /** Metres either side of a crest over which that fall is looked for. */
  val cairnReach: Double = 1_500.0,

  /** Share of hilltops in dry or upland country that carry a rock outcrop. */
  val outcropShare: Double = 0.2,

  /** Smallest hill, by radius in metres, that carries an outcrop. */
  val outcropMinRadius: Double = 60.0,

  /** Cemetery side, in metres, before the settlement's size and age are counted, and its cap. */
  val cemeteryBaseSide: Double = 12.0,
  val cemeteryMaxSide: Double = 46.0,

  /** Fields a settlement must work before it builds a windmill to grind what they grow. */
  val windmillMinFields: Int = 3
) : Params {

  init {
    require(lanternSpacing > 0.0) { "lanternSpacing must be positive" }
    require(lanternReachMax >= lanternReachMin && lanternReachMin >= 0.0) { "lantern reach range is inverted" }
    require(milestoneSpacing > 0.0) { "milestoneSpacing must be positive" }
    require(cairnRelief > 0.0 && cairnReach > 0.0) { "cairn relief and reach must be positive" }
    require(outcropShare in 0.0..1.0) { "outcropShare must be in [0,1]" }
    require(cemeteryMaxSide >= cemeteryBaseSide && cemeteryBaseSide > 0.0) { "cemetery sides are inverted" }
    require(windmillMinFields >= 0) { "windmillMinFields must not be negative" }
  }

  override fun digest() = ParamsDigest()
    .put("lanternSpacing", lanternSpacing)
    .put("lanternReachMin", lanternReachMin)
    .put("lanternReachMax", lanternReachMax)
    .put("milestoneSpacing", milestoneSpacing)
    .put("cairnRelief", cairnRelief)
    .put("cairnReach", cairnReach)
    .put("outcropShare", outcropShare)
    .put("outcropMinRadius", outcropMinRadius)
    .put("cemeteryBaseSide", cemeteryBaseSide)
    .put("cemeteryMaxSide", cemeteryMaxSide)
    .put("windmillMinFields", windmillMinFields)
}

/**
 * The set dressing: the man-made things that say people live here.
 *
 * Piers and boats on a waterfront, a windmill above the fields, wells on the greens, a walled cemetery at the edge
 * of every village with the chronicle's dead in it, signposts where a lane meets a road, milestones along the
 * trunk roads, lanterns lighting the last few kilometres into a settlement, cairns at the top of a trail's climb,
 * a house for every roadside inn, and rock outcrops on the hilltops of dry country.
 *
 * ### Markers here, scatters there
 *
 * Everything this stage writes is **sparse and needs the whole world to decide**: which settlement a road leads
 * to, which figures were buried at home, which side of a shore is the sea. So each is a `FIXTURE` marker - one
 * point, a kind, a facing, an extent - that the chunk tier turns into exactly one prop (`voxel/FixtureProps`).
 *
 * What is dense and local is **not** here. The fences round every field, the crop in it, its haystacks and its
 * scarecrow, and the plain headstones filling a cemetery are all derived per chunk from features that already
 * exist, for the reason trees are never stored: there are far too many, and each is a pure function of something
 * a chunk can already see.
 *
 * ### Last but for the nav graph, after the hills
 *
 * A windmill wants the highest hill near the fields and a cairn wants a crest; and nothing here may stand where a
 * hill was put down after it. So this runs after `KnollStage`, and reads the final world.
 */
class DressingStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: DressingParams = DressingParams()
) : Stage {

  override val id = ID

  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, FixtureKind.catalogueDigest())

  override val dependencies = listOf(
    KnollStage.ID,
    HistoryStage.ID,
    TownStage.ID,
    EconomyStage.ID,
    SettlementStage.ID,
    CoastStage.ID,
    PondStage.ID,
    BiomeStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.FIXTURE), StageOutput.Vector(FeatureKind.CEMETERY))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val world = World.read(ctx, region)
    val out = Out(FeatureIds.allocator(id))

    for (settlement in world.settlements) {
      if (!settlement.standing) continue
      piers(ctx, world, settlement, out)
      wells(ctx, world, settlement, out)
      windmill(ctx, world, settlement, out)
      cemetery(ctx, world, settlement, out)
      lanterns(ctx, world, settlement, out)
    }
    signposts(ctx, world, out)
    milestones(ctx, world, out)
    cairns(ctx, world, out)
    inns(ctx, world, out)
    outcrops(ctx, world, out)

    return StageResult(features = out.features)
  }

  // --- What the stage reads ---------------------------------------------------------------------------------

  private class Settlement(
    val index: Int,
    val position: Vec2d,
    val tier: SettlementTier,
    val population: Int,
    val radius: Double,
    val standing: Boolean,
    val record: net.bestia.worldgen.core.SettlementRecord?
  )

  /** Everything the producers below read, from one query of the whole world. */
  private class World(
    val ctx: GenContext,
    val elevation: FloatLayer,
    val waterLevel: FloatLayer,
    val biome: IntLayer,
    val metres: Double,
    val seaLevel: Double,
    val chronicle: Chronicle,
    val settlements: List<Settlement>,
    val roads: List<PolylineFeature>,
    val bridges: List<Vec2d>,
    val coasts: List<MarkerFeature>,
    val lakes: List<AreaFeature>,
    val fields: List<AreaFeature>,
    val buildings: List<FootprintFeature>,
    val knolls: List<PointFeature>,
    val inns: List<PointMarker>
  ) {

    fun biomeAt(at: Vec2d): Biome {
      val cellX = Math.floor(at.x / metres).toInt()
      val cellY = Math.floor(at.y / metres).toInt()
      return Biome.entries[biome[cellX, cellY]]
    }

    fun isDry(at: Vec2d): Boolean =
      elevation.sampleBilinear(at.x, at.y) > seaLevel + DRY_FREEBOARD && waterLevel.sampleBilinear(at.x, at.y).isNaN()

    /** The settlement whose built ground contains [at], or the nearest within [slack] of it. */
    fun settlementNear(at: Vec2d, slack: Double): Settlement? =
      settlements.filter { it.position.distanceTo(at) <= it.radius + slack }.minByOrNull { it.position.distanceTo(at) }

    /**
     * Whether a fixture of footprint [radius] may stand at [at]: dry, and clear of everything built or flowing.
     *
     * Asked of the feature index around the spot, by geometry: a road or street corridor, a river, a building, a
     * field, standing water, a bridge. Each fixture producer adds what is special to it - a pier wants water - and
     * relies on this for the rest.
     */
    fun isClear(at: Vec2d, radius: Double, allowFields: Boolean = false): Boolean {
      if (!isDry(at)) return false
      val reach = radius + CLEARANCE_QUERY
      for (feature in ctx.features.query(Aabb(at.x - reach, at.y - reach, at.x + reach, at.y + reach))) {
        val blocked = when (feature.kind) {
          FeatureKind.ROAD, FeatureKind.STREET -> {
            val road = feature as? PolylineFeature ?: continue
            road.centerline.project(at).distance < radius + treadOf(road) + ROAD_MARGIN
          }
          FeatureKind.RIVER_CHANNEL -> {
            val river = feature as? PolylineFeature ?: continue
            river.centerline.project(at).distance < radius + feature.corridorWidthMax * 0.5 + 2.0
          }
          FeatureKind.BUILDING -> {
            val building = feature as? FootprintFeature ?: continue
            building.center.distanceTo(at) < radius + max(building.halfLength, building.halfWidth) + 1.0
          }
          FeatureKind.FIELD -> !allowFields && (feature as? AreaFeature)?.contains(at.x, at.y) == true
          FeatureKind.LAKE, FeatureKind.OXBOW_LAKE -> (feature as? AreaFeature)?.let {
            it.contains(at.x, at.y) || it.ring.project(at).distance < radius + 2.0
          } == true
          FeatureKind.BRIDGE -> (feature as? PointMarker)?.position?.distanceTo(at)?.let { it < radius + 20.0 } == true
          FeatureKind.TOWN_WALL -> (feature as? PolylineFeature)?.centerline?.project(at)?.distance
            ?.let { it < radius + 4.0 } == true
          else -> false
        }
        if (blocked) return false
      }
      return true
    }

    companion object {

      fun read(ctx: GenContext, region: CellRegion): World {
        val all = ctx.features.query(region.toWorld())
        val chronicle = ctx.chronicle()

        val gradings = all.filter { it.kind == FeatureKind.SETTLEMENT_GRADING }.filterIsInstance<PointFeature>()
        val settlements = all.filter { it.kind == FeatureKind.SETTLEMENT }.filterIsInstance<PointMarker>()
          .mapNotNull { marker ->
            runCatching {
              val index = marker.attribute(SettlementChannels.INDEX).toInt()
              val tier = SettlementTier.entries[marker.attribute(SettlementChannels.TIER).toInt()]
              val record = chronicle.settlements.getOrNull(index)?.takeIf { it.index == index }
              val radius = gradings.minByOrNull { it.center.distanceTo(marker.position) }
                ?.takeIf { it.center.distanceTo(marker.position) < 1.0 }?.radius ?: tier.footprintRadius
              Settlement(
                index = index,
                position = marker.position,
                tier = tier,
                population = marker.attribute(SettlementChannels.POPULATION).toInt(),
                radius = radius,
                standing = record == null || !record.isRuin,
                record = record
              )
            }.getOrNull()
          }
          .sortedBy { it.index }

        fun <T : VectorFeature> sorted(list: List<T>) = list.sortedBy { it.id.value }

        return World(
          ctx = ctx,
          elevation = ctx.layers.float(LayerId.ELEVATION),
          waterLevel = ctx.layers.float(LayerId.WATER_LEVEL),
          biome = ctx.layers.int(LayerId.BIOME),
          metres = region.resolution.metresPerCell,
          seaLevel = ctx.config.seaLevel,
          chronicle = chronicle,
          settlements = settlements,
          roads = sorted(all.filter { it.kind == FeatureKind.ROAD }.filterIsInstance<PolylineFeature>()),
          bridges = all.filter { it.kind == FeatureKind.BRIDGE }.filterIsInstance<PointMarker>().map { it.position },
          coasts = sorted(all.filter { it.kind == FeatureKind.COASTLINE }.filterIsInstance<MarkerFeature>()),
          lakes = sorted(all.filter { it.kind == FeatureKind.LAKE }.filterIsInstance<AreaFeature>()),
          fields = sorted(all.filter { it.kind == FeatureKind.FIELD }.filterIsInstance<AreaFeature>()),
          buildings = sorted(all.filter { it.kind == FeatureKind.BUILDING }.filterIsInstance<FootprintFeature>()),
          knolls = sorted(all.filter { it.kind == FeatureKind.KNOLL }.filterIsInstance<PointFeature>()),
          inns = sorted(all.filter { it.kind == FeatureKind.ROADSIDE_INN }.filterIsInstance<PointMarker>())
        )
      }
    }
  }

  /** What the producers write, and the one id allocator they share. */
  private class Out(val nextId: () -> FeatureId) {
    val features = ArrayList<VectorFeature>()

    fun fixture(
      kind: FixtureKind,
      at: Vec2d,
      yaw: Double = 0.0,
      halfLength: Double = 0.0,
      halfWidth: Double = 0.0,
      height: Double = kind.heightM,
      elevation: Double = Double.NaN,
      style: Int = 0,
      settlement: Int = -1,
      order: Int = 0,
      figure: Int = -1,
      destinations: List<Destination> = emptyList()
    ) {
      val builder = StationTable.Builder(stationCount = 1)
        .channel(FixtureChannels.KIND) { kind.ordinal.toDouble() }
        .channel(FixtureChannels.YAW) { yaw }
        .channel(FixtureChannels.HALF_LENGTH) { halfLength }
        .channel(FixtureChannels.HALF_WIDTH) { halfWidth }
        .channel(FixtureChannels.HEIGHT) { height }
        .channel(FixtureChannels.ELEVATION) { elevation }
        .channel(FixtureChannels.STYLE) { style.toDouble() }
        .channel(FixtureChannels.SETTLEMENT) { settlement.toDouble() }
        .channel(FixtureChannels.ORDER) { order.toDouble() }
        .channel(FixtureChannels.FIGURE) { figure.toDouble() }
      if (kind.readable && destinations.isNotEmpty()) {
        for (arm in 0 until FixtureChannels.MAX_DESTINATIONS) {
          val d = destinations.getOrNull(arm)
          builder.channel(FixtureChannels.destination(arm)) { (d?.settlement ?: -1).toDouble() }
          builder.channel(FixtureChannels.distance(arm)) { d?.metres ?: 0.0 }
          builder.channel(FixtureChannels.bearing(arm)) { d?.bearing ?: 0.0 }
        }
      }
      features.add(PointMarker(id = nextId(), kind = FeatureKind.FIXTURE, position = at, attributes = builder.build()))
    }
  }

  private class Destination(val settlement: Int, val metres: Double, val bearing: Double)

  // --- Waterfront: piers and boats -------------------------------------------------------------------------

  /**
   * Piers out from the waterfront of a settlement on the sea or on a lake, with a boat or two tied up at each.
   *
   * The root is the nearest point of the shoreline to the settlement - the traced `COASTLINE` for the sea, the
   * lake's ring for a lake - and the pier runs out along the shore's normal, into whichever side is water. A
   * cliff or a salt marsh gets none: nobody builds a pier off either. A city gets three along its waterfront and
   * a T-head on the longest; a town two; a village one.
   */
  private fun piers(ctx: GenContext, world: World, settlement: Settlement, out: Out) {
    if (settlement.tier == SettlementTier.HAMLET) return
    val reach = settlement.radius + PIER_SHORE_REACH
    val h = GenRng.hash(ctx.seed, id.hash, PIER_SALT, settlement.index.toLong())
    val count = when (settlement.tier) {
      SettlementTier.CITY -> 3
      SettlementTier.TOWN -> 2
      else -> 1
    }
    val length = when (settlement.tier) {
      SettlementTier.CITY -> 38.0
      SettlementTier.TOWN -> 30.0
      else -> 22.0
    } * (0.85 + 0.3 * GenRng.unit(h))
    val halfWidth = if (settlement.tier == SettlementTier.VILLAGE) 1.4 else 2.0

    // The sea first: a coastline within reach.
    val coast = world.coasts.mapNotNull { line ->
      if (!line.bbox.expanded(reach).contains(settlement.position.x, settlement.position.y)) return@mapNotNull null
      val p = line.centerline.project(settlement.position)
      if (p.distance > reach) null else line to p
    }.minByOrNull { it.second.distance }

    if (coast != null) {
      val (line, root) = coast
      val shore = line.stations?.let { table ->
        runCatching { ShoreKind.entries[table.sample(table.channel(CoastChannels.SHORE_KIND), root.u).toInt()] }
          .getOrNull()
      }
      if (shore == ShoreKind.SEA_CLIFF || shore == ShoreKind.SALT_MARSH) return

      for (k in 0 until count) {
        val offset = if (k == 0) 0.0 else (if (k % 2 == 1) 1.0 else -1.0) * PIER_SPACING * ((k + 1) / 2)
        val s = (root.s + offset).coerceIn(0.0, line.centerline.length)
        val at = line.centerline.pointAt(s)
        val tangent = line.centerline.tangentAt(s)
        val normal = Vec2d(-tangent.y, tangent.x)
        val sea = seaward(world, at, normal) ?: continue
        if (nearRiver(ctx, at, PIER_RIVER_CLEARANCE)) continue
        val thisLength = if (k == 0) length else length * 0.8
        pier(world, out, settlement, at, sea, thisLength, halfWidth, world.seaLevel, h + k,
          tHead = k == 0 && settlement.tier == SettlementTier.CITY)
        if (k == 0 && settlement.tier != SettlementTier.CITY) beachedBoats(ctx, world, out, settlement, line, s, sea, shore)
      }
      return
    }

    // Else a lake within reach.
    val lake = world.lakes.mapNotNull { area ->
      if (!area.bbox.expanded(reach).contains(settlement.position.x, settlement.position.y)) return@mapNotNull null
      val p = area.ring.project(settlement.position)
      if (p.distance > reach) null else area to p
    }.minByOrNull { it.second.distance } ?: return

    val (area, root) = lake
    val surface = area.perimeter?.let { table ->
      runCatching { table.sample(table.channel(LakeChannels.SURFACE_ELEVATION), root.u) }.getOrNull()
    } ?: return
    val toward = (root.point - settlement.position).let { d ->
      val n = d.length
      if (n < 1e-6) return else d * (1.0 / n)
    }
    // Into the lake: the side of the root that the ring contains.
    val inward = if (area.contains(root.point.x + toward.x * 4.0, root.point.y + toward.y * 4.0)) toward else -toward
    pier(world, out, settlement, root.point, inward, length * 0.7, halfWidth, surface, h, tHead = false)
  }

  private fun pier(
    world: World,
    out: Out,
    settlement: Settlement,
    root: Vec2d,
    direction: Vec2d,
    length: Double,
    halfWidth: Double,
    waterSurface: Double,
    h: Long,
    tHead: Boolean
  ) {
    val yaw = atan2(direction.y, direction.x)
    val deck = waterSurface + DECK_FREEBOARD
    val centre = root + direction * (length * 0.5)
    out.fixture(FixtureKind.PIER, centre, yaw, length * 0.5, halfWidth, elevation = deck, settlement = settlement.index)
    if (tHead) {
      out.fixture(
        FixtureKind.PIER, root + direction * (length + halfWidth), yaw + PI / 2, T_HEAD_HALF_LENGTH, halfWidth,
        elevation = deck, settlement = settlement.index
      )
    }

    // A boat or two tied up alongside, on whichever side the hash picks, then the other.
    val across = Vec2d(-direction.y, direction.x)
    val boats = 1 + (GenRng.unit(GenRng.mix64(h + 7)) * 2.0).toInt().coerceAtMost(1)
    for (b in 0 until boats) {
      val side = if ((b + (h and 1L).toInt()) % 2 == 0) 1.0 else -1.0
      val along = length * (0.45 + 0.35 * GenRng.unit(GenRng.mix64(h + 11 + b)))
      val at = root + direction * along + across * (side * (halfWidth + BOAT_HALF_WIDTH + 0.6))
      out.fixture(
        FixtureKind.BOAT, at, yaw, BOAT_HALF_LENGTH, BOAT_HALF_WIDTH, elevation = waterSurface,
        style = b % 2, settlement = settlement.index
      )
    }
  }

  /** A boat or two drawn up on the beach beside a village's pier, when the shore is a beach. */
  private fun beachedBoats(
    ctx: GenContext,
    world: World,
    out: Out,
    settlement: Settlement,
    line: MarkerFeature,
    s: Double,
    sea: Vec2d,
    shore: ShoreKind?
  ) {
    if (shore != ShoreKind.SAND_BEACH && shore != ShoreKind.SHINGLE_BEACH) return
    val h = GenRng.hash(ctx.seed, id.hash, BOAT_SALT, settlement.index.toLong())
    val count = (GenRng.unit(h) * 3.0).toInt()
    for (k in 0 until count) {
      val along = s + (if (k == 0) 1.0 else -1.0) * (18.0 + 30.0 * GenRng.unit(GenRng.mix64(h + k)))
      if (along < 0.0 || along > line.centerline.length) continue
      val at = line.centerline.pointAt(along) - sea * BEACH_SETBACK
      if (!world.isClear(at, BOAT_HALF_LENGTH)) continue
      val tangent = line.centerline.tangentAt(along)
      out.fixture(
        FixtureKind.BOAT, at, atan2(sea.y, sea.x) + (GenRng.unit(GenRng.mix64(h + 9 + k)) - 0.5) * 0.6,
        BOAT_HALF_LENGTH, BOAT_HALF_WIDTH, style = (k + 1) % 2, settlement = settlement.index
      ).also { if (tangent.length < 0.0) Unit }
    }
  }

  /** Which way along [normal] from [at] is the sea, or null if neither side is clearly water. */
  private fun seaward(world: World, at: Vec2d, normal: Vec2d): Vec2d? {
    val plus = at + normal * SEAWARD_PROBE
    val minus = at - normal * SEAWARD_PROBE
    val hp = world.elevation.sampleBilinear(plus.x, plus.y)
    val hm = world.elevation.sampleBilinear(minus.x, minus.y)
    return when {
      hp < world.seaLevel && hp < hm -> normal
      hm < world.seaLevel && hm < hp -> -normal
      else -> null
    }
  }

  private fun nearRiver(ctx: GenContext, at: Vec2d, clearance: Double): Boolean =
    ctx.features.query(Aabb(at.x - clearance, at.y - clearance, at.x + clearance, at.y + clearance))
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }
      .filterIsInstance<PolylineFeature>()
      .any { it.centerline.project(at).distance < clearance }

  // --- Villages: wells, windmills, cemeteries --------------------------------------------------------------

  /**
   * Wells on the green or the square: one for a hamlet or a village, more for a town or a city.
   *
   * Searched outwards from the centre on rings until a spot is clear of streets and buildings, so a village's well
   * stands on its green or at its crossroads rather than in the road.
   */
  private fun wells(ctx: GenContext, world: World, settlement: Settlement, out: Out) {
    val count = when (settlement.tier) {
      SettlementTier.CITY -> 4
      SettlementTier.TOWN -> 2
      else -> 1
    }
    val h = GenRng.hash(ctx.seed, id.hash, WELL_SALT, settlement.index.toLong())
    val placed = ArrayList<Vec2d>()
    for (k in 0 until count) {
      val anchor = if (k == 0) {
        settlement.position
      } else {
        val angle = 2.0 * PI * GenRng.unit(GenRng.mix64(h + k))
        val r = settlement.radius * (0.2 + 0.3 * GenRng.unit(GenRng.mix64(h + 31 + k)))
        settlement.position + Vec2d(cos(angle), sin(angle)) * r
      }
      val at = searchAround(world, anchor, WELL_RADIUS, h + k * 97, maxRing = 60.0) {
        placed.none { p -> p.distanceTo(it) < WELL_SEPARATION }
      } ?: continue
      placed.add(at)
      out.fixture(FixtureKind.WELL, at, yaw = 2.0 * PI * GenRng.unit(GenRng.mix64(h + 53 + k)), settlement = settlement.index)
    }
  }

  /**
   * A windmill on the highest rise above a settlement's fields, if it works enough of them to need one.
   *
   * A hill near the fields first - the biggest, since a hill's height is its radius times a share drawn per
   * cluster - and otherwise the highest clear spot on a ring around the settlement.
   */
  private fun windmill(ctx: GenContext, world: World, settlement: Settlement, out: Out) {
    if (settlement.tier == SettlementTier.HAMLET) return
    val fields = world.fields.count { field ->
      field.perimeter?.let { runCatching { it.valueAt(it.channel(FieldChannels.SETTLEMENT), 0).toInt() }.getOrNull() } ==
          settlement.index
    }
    if (fields < params.windmillMinFields) return

    val h = GenRng.hash(ctx.seed, id.hash, MILL_SALT, settlement.index.toLong())
    val reach = settlement.radius + WINDMILL_REACH
    val hill = world.knolls
      .filter { it.center.distanceTo(settlement.position) in settlement.radius..reach }
      .filter { world.isClear(it.center, WINDMILL_RADIUS) }
      .maxByOrNull { it.radius }

    val at = hill?.center ?: run {
      var best: Vec2d? = null
      var bestHeight = Double.NEGATIVE_INFINITY
      for (k in 0 until WINDMILL_CANDIDATES) {
        val angle = 2.0 * PI * k / WINDMILL_CANDIDATES + GenRng.unit(h)
        val r = settlement.radius + 120.0 + 350.0 * GenRng.unit(GenRng.mix64(h + k))
        val p = settlement.position + Vec2d(cos(angle), sin(angle)) * r
        val height = world.elevation.sampleBilinear(p.x, p.y)
        if (height > bestHeight && world.isClear(p, WINDMILL_RADIUS)) {
          best = p
          bestHeight = height
        }
      }
      best
    } ?: return

    out.fixture(
      FixtureKind.WINDMILL, at, yaw = 2.0 * PI * GenRng.unit(GenRng.mix64(h + 5)), halfLength = 3.5, halfWidth = 3.5,
      settlement = settlement.index
    )
  }

  /**
   * A walled cemetery at the edge of every village and town, sized by how many have lived and died there.
   *
   * Beside the temple when there is one, since that is where the dead are buried; otherwise just outside the built
   * edge, on the first side that is clear of fields, roads, water and hills. Everyone the chronicle remembers who
   * died at home and has no tomb elsewhere gets a named stone on the first plots; the chunk tier fills the rest
   * with plain headstones, more of them the longer the place has stood.
   */
  private fun cemetery(ctx: GenContext, world: World, settlement: Settlement, out: Out) {
    if (settlement.tier == SettlementTier.HAMLET) return
    val record = settlement.record ?: return
    if (!record.wasFounded) return

    val age = (world.chronicle.presentYear - record.foundedYear).coerceAtLeast(1)
    val side = (params.cemeteryBaseSide + sqrt(settlement.population.toDouble() * age / CEMETERY_SCALE))
      .coerceAtMost(params.cemeteryMaxSide)
    val halfLength = side * 0.5
    val halfWidth = side * 0.38
    val h = GenRng.hash(ctx.seed, id.hash, CEMETERY_SALT, settlement.index.toLong())

    val temple = world.buildings.firstOrNull { building ->
      building.attributes?.let { a ->
        runCatching {
          a.valueAt(a.channel(BuildingChannels.SETTLEMENT), 0).toInt() == settlement.index &&
              BuildingFunction.entries[a.valueAt(a.channel(BuildingChannels.FUNCTION), 0).toInt()] == BuildingFunction.TEMPLE
        }.getOrNull()
      } == true
    }

    val candidates = ArrayList<Vec2d>()
    if (temple != null) {
      val out1 = (temple.center - settlement.position).let { d -> if (d.length < 1e-6) Vec2d(1.0, 0.0) else d * (1.0 / d.length) }
      for (k in 0 until 6) {
        val turn = (k / 2) * 0.6 * (if (k % 2 == 0) 1.0 else -1.0)
        val dir = Vec2d(out1.x * cos(turn) - out1.y * sin(turn), out1.x * sin(turn) + out1.y * cos(turn))
        candidates.add(temple.center + dir * (max(temple.halfLength, temple.halfWidth) + halfLength + 6.0))
      }
    }
    val start = 2.0 * PI * GenRng.unit(h)
    for (k in 0 until CEMETERY_CANDIDATES) {
      val angle = start + 2.0 * PI * k / CEMETERY_CANDIDATES
      candidates.add(settlement.position + Vec2d(cos(angle), sin(angle)) * (settlement.radius * 0.8 + halfLength))
    }

    for (centre in candidates) {
      val toward = settlement.position - centre
      // Faces the settlement, so the gate on the near wall opens towards it.
      val yaw = atan2(toward.y, toward.x)
      val ring = Plots.rectangle(centre, yaw, halfLength, halfWidth)
      if (!(ring.vertices + centre).all { world.isClear(it, 2.0) }) continue
      if (world.knolls.any { it.center.distanceTo(centre) < it.radius + halfLength }) continue

      val plots = Plots.cemetery(centre, yaw, halfLength, halfWidth)
      val named = world.chronicle.figures
        .filter { it.homeSettlement == settlement.index && it.deathYear != 0 && it.restingSite == -1 }
        .sortedWith(compareBy({ it.deathYear }, { it.index }))
        .take(plots.size)
      val fill = (age / CEMETERY_FULL_AGE).coerceIn(0.25, 0.85)
      val wall = if (stoneCountry(world.biomeAt(centre)) || GenRng.unit(GenRng.mix64(h + 3)) < 0.35) {
        FenceStyle.DRY_STONE
      } else {
        FenceStyle.POST_AND_RAIL
      }

      val table = StationTable.Builder(stationCount = ring.vertexCount, periodic = true)
        .channel(CemeteryChannels.SETTLEMENT) { settlement.index.toDouble() }
        .channel(CemeteryChannels.NAMED) { named.size.toDouble() }
        .channel(CemeteryChannels.FILL) { fill }
        .channel(CemeteryChannels.YAW) { yaw }
        .channel(CemeteryChannels.HALF_LENGTH) { halfLength }
        .channel(CemeteryChannels.HALF_WIDTH) { halfWidth }
        .channel(CemeteryChannels.WALL) { wall.ordinal.toDouble() }
        .build()
      out.features.add(AreaFeature(id = out.nextId(), kind = FeatureKind.CEMETERY, ring = ring, perimeter = table))

      for ((k, figure) in named.withIndex()) {
        out.fixture(FixtureKind.NAMED_GRAVE, plots[k], yaw = yaw, settlement = settlement.index, figure = figure.index)
      }
      return
    }
  }

  // --- Ways: lanterns, signposts, milestones, cairns, inns -------------------------------------------------

  /**
   * Lanterns along every road out of a settlement, from its built edge for two or three kilometres.
   *
   * A pale magic flame that burns after dark - the client lights them by the clock. Spaced a little under a
   * stone's throw apart, alternating sides on a cobbled road and keeping to one side on a dirt road or a trail.
   * Each carries its settlement and its place along the line, so the settlement's lamplighter can walk them in
   * order.
   */
  private fun lanterns(ctx: GenContext, world: World, settlement: Settlement, out: Out) {
    if (settlement.tier == SettlementTier.HAMLET) return
    val h = GenRng.hash(ctx.seed, id.hash, LANTERN_SALT, settlement.index.toLong())
    val reach = params.lanternReachMin + (params.lanternReachMax - params.lanternReachMin) * GenRng.unit(h)
    var order = 0

    for (road in world.roads) {
      val line = road.centerline
      val fromStart = line.points.first().distanceTo(settlement.position) <= settlement.radius + 30.0
      val fromEnd = line.points.last().distanceTo(settlement.position) <= settlement.radius + 30.0
      if (!fromStart && !fromEnd) continue
      val roadClass = RoadClass.of(road) ?: RoadClass.TRAIL
      val tread = roadClass.halfWidth + LANTERN_SETBACK
      val oneSide = if (GenRng.unit(GenRng.mix64(h + road.id.value)) < 0.5) 1.0 else -1.0

      // Walked from the settlement's end outwards; `s` is arc length from that end.
      var walked = 0.0
      var started = false
      var k = 0
      var s = 0.0
      while (s < line.length) {
        val along = if (fromStart) s else line.length - s
        val at = line.pointAt(along)
        if (!started) {
          if (at.distanceTo(settlement.position) > settlement.radius) started = true else {
            s += STEP_INSIDE
            continue
          }
        }
        if (walked > reach) break
        // Stop at the next settlement's edge rather than lighting its approach for it.
        if (world.settlements.any { it !== settlement && it.position.distanceTo(at) < it.radius }) break

        val tangent = line.tangentAt(along).let { if (fromStart) it else -it }
        val normal = Vec2d(-tangent.y, tangent.x)
        val side = if (roadClass == RoadClass.COBBLED && k % 2 == 1) -oneSide else oneSide
        val spot = at + normal * (tread * side)
        if (world.bridges.none { it.distanceTo(at) < LANTERN_BRIDGE_CLEARANCE } &&
          world.isDry(spot) && !nearRiver(ctx, spot, 6.0)
        ) {
          out.fixture(
            FixtureKind.LANTERN, spot, yaw = atan2(tangent.y, tangent.x), settlement = settlement.index, order = order++
          )
        }
        k++
        val step = params.lanternSpacing * (0.9 + 0.2 * GenRng.unit(GenRng.mix64(h + road.id.value * 31 + k)))
        s += step
        walked += step
      }
    }
  }

  /**
   * A signpost wherever a lane ends on another road outside a settlement, naming where each way leads.
   *
   * `SettlementStage` routes a village's spur to end exactly on the nearest trunk, so a junction is a road end
   * lying on another road's line. Each arm is followed to the road's own end and named for the settlement there;
   * an arm that ends on yet another road names nothing rather than guessing.
   */
  private fun signposts(ctx: GenContext, world: World, out: Out) {
    for (spur in world.roads) {
      for (atStart in listOf(true, false)) {
        val end = if (atStart) spur.centerline.points.first() else spur.centerline.points.last()
        if (world.settlementNear(end, 40.0) != null) continue
        val trunk = world.roads.firstOrNull { other ->
          other !== spur && other.bbox.expanded(JUNCTION_SNAP).contains(end.x, end.y) && run {
            val p = other.centerline.project(end)
            p.distance < JUNCTION_SNAP && p.s > JUNCTION_SNAP && p.s < other.centerline.length - JUNCTION_SNAP
          }
        } ?: continue

        val p = trunk.centerline.project(end)
        val spurIn = if (atStart) spur.centerline.tangentAt(JUNCTION_SNAP) else -spur.centerline.tangentAt(spur.centerline.length - JUNCTION_SNAP)
        val trunkClass = RoadClass.of(trunk) ?: RoadClass.DIRT_ROAD
        val spurClass = RoadClass.of(spur) ?: RoadClass.TRAIL
        val trunkTangent = trunk.centerline.tangentAt(p.s)
        val corner = p.point + spurIn * (trunkClass.halfWidth + 2.5) + trunkTangent * (spurClass.halfWidth + 2.0)
        if (!world.isClear(corner, 0.6)) continue

        val destinations = listOfNotNull(
          destinationAlong(world, corner, trunk, p.s, toStart = true),
          destinationAlong(world, corner, trunk, p.s, toStart = false),
          destinationAlong(world, corner, spur, if (atStart) 0.0 else spur.centerline.length, toStart = !atStart)
        )
        if (destinations.isEmpty()) continue
        out.fixture(FixtureKind.SIGNPOST, corner, yaw = atan2(spurIn.y, spurIn.x), destinations = destinations)
      }
    }
  }

  /** The settlement at the end of [road] in one direction from arc length [s], or null if none is there. */
  private fun destinationAlong(world: World, from: Vec2d, road: PolylineFeature, s: Double, toStart: Boolean): Destination? {
    val line = road.centerline
    val end = if (toStart) line.points.first() else line.points.last()
    val settlement = world.settlementNear(end, 80.0) ?: return null
    val metres = if (toStart) s else line.length - s
    val delta = settlement.position - from
    return Destination(settlement.index, metres, atan2(delta.y, delta.x))
  }

  /** Milestones along every dirt road and cobbled road, naming the settlement each way and how far. */
  private fun milestones(ctx: GenContext, world: World, out: Out) {
    for (road in world.roads) {
      val roadClass = RoadClass.of(road) ?: continue
      if (roadClass == RoadClass.TRAIL) continue
      val line = road.centerline
      val first = world.settlementNear(line.points.first(), 80.0) ?: continue
      val last = world.settlementNear(line.points.last(), 80.0) ?: continue
      if (first === last) continue

      var s = params.milestoneSpacing
      while (s < line.length - params.milestoneSpacing * 0.5) {
        val at = line.pointAt(s)
        val tangent = line.tangentAt(s)
        val normal = Vec2d(-tangent.y, tangent.x)
        val side = if (((road.id.value + s.toLong()) and 1L) == 0L) 1.0 else -1.0
        val spot = at + normal * ((roadClass.halfWidth + MILESTONE_SETBACK) * side)
        val inside = world.settlements.any { it.position.distanceTo(at) < it.radius + 100.0 }
        if (!inside && world.isClear(spot, 0.4)) {
          out.fixture(
            FixtureKind.MILESTONE, spot, yaw = atan2(tangent.y, tangent.x),
            destinations = listOf(
              Destination(first.index, s, atan2(first.position.y - spot.y, first.position.x - spot.x)),
              Destination(last.index, line.length - s, atan2(last.position.y - spot.y, last.position.x - spot.x))
            )
          )
        }
        s += params.milestoneSpacing
      }
    }
  }

  /** A cairn beside every trail where it tops a climb: a crest with a real fall on both sides. */
  private fun cairns(ctx: GenContext, world: World, out: Out) {
    for (road in world.roads) {
      if (RoadClass.of(road) != RoadClass.TRAIL) continue
      val line = road.centerline
      val count = (line.length / CAIRN_STEP).toInt()
      if (count < 3) continue
      val heights = DoubleArray(count + 1) { k ->
        val p = line.pointAt(min(line.length, k * CAIRN_STEP))
        world.elevation.sampleBilinear(p.x, p.y)
      }
      val window = (params.cairnReach / CAIRN_STEP).toInt().coerceAtLeast(1)
      var lastCairn = -window
      for (k in 1 until count) {
        if (k - lastCairn < window) continue
        val here = heights[k]
        val lo = (k - window).coerceAtLeast(0)
        val hi = (k + window).coerceAtMost(count)
        if ((lo..hi).any { heights[it] > here }) continue
        val before = (lo until k).minOf { heights[it] }
        val after = (k + 1..hi).minOf { heights[it] }
        if (here - before < params.cairnRelief || here - after < params.cairnRelief) continue

        val s = k * CAIRN_STEP
        val tangent = line.tangentAt(s)
        val normal = Vec2d(-tangent.y, tangent.x)
        val spot = line.pointAt(s) + normal * (RoadClass.TRAIL.halfWidth + 2.0)
        if (!world.isClear(spot, 1.0)) continue
        out.fixture(FixtureKind.CAIRN, spot, yaw = GenRng.unit(GenRng.hash(ctx.seed, road.id.value, k.toLong())) * 2.0 * PI)
        lastCairn = k
      }
    }
  }

  /**
   * The house of each roadside inn, beside the road and parallel to it, with a well and a hitching rail.
   *
   * `EconomyStage` placed the inn as a business on the road; nothing drew it. The house goes on whichever side is
   * clear, the well at its gable end and the rail between it and the road.
   */
  private fun inns(ctx: GenContext, world: World, out: Out) {
    for (inn in world.inns) {
      val road = world.roads.minByOrNull { it.centerline.project(inn.position).distance } ?: continue
      val p = road.centerline.project(inn.position)
      if (p.distance > INN_ROAD_SNAP) continue
      val tangent = road.centerline.tangentAt(p.s)
      val normal = Vec2d(-tangent.y, tangent.x)
      val roadHalf = (RoadClass.of(road) ?: RoadClass.DIRT_ROAD).halfWidth
      val yaw = atan2(tangent.y, tangent.x)

      for (side in listOf(1.0, -1.0)) {
        val house = p.point + normal * (side * (roadHalf + INN_SETBACK + INN_HALF_WIDTH))
        if (!world.isClear(house, INN_HALF_LENGTH)) continue
        out.fixture(FixtureKind.INN, house, yaw, INN_HALF_LENGTH, INN_HALF_WIDTH)
        val well = house + tangent * (INN_HALF_LENGTH + 3.5)
        if (world.isClear(well, WELL_RADIUS)) out.fixture(FixtureKind.WELL, well, yaw)
        val rail = p.point + normal * (side * (roadHalf + INN_SETBACK * 0.5))
        out.fixture(FixtureKind.FENCE, rail, yaw, halfLength = 3.0, halfWidth = 0.15, style = FenceStyle.POST_AND_RAIL.ordinal)
        break
      }
    }
  }

  /** Rock outcrops on the tops of hills in dry and upland country. */
  private fun outcrops(ctx: GenContext, world: World, out: Out) {
    for (hill in world.knolls) {
      if (hill.radius < params.outcropMinRadius) continue
      if (!stoneCountry(world.biomeAt(hill.center))) continue
      val h = GenRng.hash(ctx.seed, id.hash, OUTCROP_SALT, hill.id.value)
      if (GenRng.unit(h) >= params.outcropShare) continue

      val count = 1 + (GenRng.unit(GenRng.mix64(h + 1)) * 3.0).toInt().coerceAtMost(2)
      for (k in 0 until count) {
        val angle = 2.0 * PI * GenRng.unit(GenRng.mix64(h + 2 + k))
        val r = hill.radius * 0.22 * GenRng.unit(GenRng.mix64(h + 5 + k))
        val at = hill.center + Vec2d(cos(angle), sin(angle)) * r
        val size = 2.5 + 3.0 * GenRng.unit(GenRng.mix64(h + 9 + k))
        out.fixture(
          FixtureKind.OUTCROP, at, yaw = 2.0 * PI * GenRng.unit(GenRng.mix64(h + 13 + k)),
          halfLength = size, halfWidth = size * 0.7, height = size * 0.9, style = k % 3
        )
      }
    }
  }

  // --- Helpers ----------------------------------------------------------------------------------------------

  /** The first clear spot on widening rings around [anchor], or null within [maxRing] metres. */
  private fun searchAround(
    world: World,
    anchor: Vec2d,
    radius: Double,
    h: Long,
    maxRing: Double,
    accept: (Vec2d) -> Boolean
  ): Vec2d? {
    if (world.isClear(anchor, radius) && accept(anchor)) return anchor
    val start = 2.0 * PI * GenRng.unit(h)
    var ring = SEARCH_STEP
    while (ring <= maxRing) {
      val steps = max(6, (2.0 * PI * ring / SEARCH_STEP).toInt())
      for (k in 0 until steps) {
        val angle = start + 2.0 * PI * k / steps
        val p = anchor + Vec2d(cos(angle), sin(angle)) * ring
        if (world.isClear(p, radius) && accept(p)) return p
      }
      ring += SEARCH_STEP
    }
    return null
  }

  private fun stoneCountry(biome: Biome): Boolean = when (biome) {
    Biome.DRYLAND, Biome.DESERT, Biome.BADLANDS, Biome.TUNDRA, Biome.ALPINE, Biome.COLD_DESERT -> true
    else -> false
  }

  companion object {

    val ID = StageId("dressing")

    /** Half-width of a road's tread beside a fixture, for the clearance test: the class's, or a street's guess. */
    internal fun treadOf(road: PolylineFeature): Double = RoadClass.of(road)?.halfWidth ?: STREET_HALF_WIDTH

    private const val STREET_HALF_WIDTH = 4.0

    /** Metres a fixture keeps between its own footprint and a road's tread. */
    private const val ROAD_MARGIN = 0.5
    private const val DRY_FREEBOARD = 0.5
    private const val CLEARANCE_QUERY = 40.0

    private const val PIER_SHORE_REACH = 500.0
    private const val PIER_SPACING = 70.0
    private const val PIER_RIVER_CLEARANCE = 40.0
    private const val SEAWARD_PROBE = 150.0
    private const val DECK_FREEBOARD = 1.2
    private const val T_HEAD_HALF_LENGTH = 7.0
    private const val BOAT_HALF_LENGTH = 2.6
    private const val BOAT_HALF_WIDTH = 0.9
    private const val BEACH_SETBACK = 6.0

    private const val WELL_RADIUS = 1.6
    private const val WELL_SEPARATION = 40.0
    private const val SEARCH_STEP = 4.0

    private const val WINDMILL_REACH = 1_200.0
    private const val WINDMILL_RADIUS = 4.0
    private const val WINDMILL_CANDIDATES = 16

    private const val CEMETERY_SCALE = 200.0
    private const val CEMETERY_FULL_AGE = 350.0
    private const val CEMETERY_CANDIDATES = 16

    private const val LANTERN_SETBACK = 1.6
    private const val LANTERN_BRIDGE_CLEARANCE = 25.0
    private const val STEP_INSIDE = 20.0

    private const val JUNCTION_SNAP = 12.0
    private const val MILESTONE_SETBACK = 1.5
    private const val CAIRN_STEP = 60.0

    private const val INN_ROAD_SNAP = 60.0
    private const val INN_SETBACK = 4.0
    private const val INN_HALF_LENGTH = 7.0
    private const val INN_HALF_WIDTH = 5.0

    private const val PIER_SALT = 0x50494552L
    private const val BOAT_SALT = 0x424F4154L
    private const val WELL_SALT = 0x57454C4CL
    private const val MILL_SALT = 0x4D494C4CL
    private const val CEMETERY_SALT = 0x43454D54L
    private const val LANTERN_SALT = 0x4C414E54L
    private const val OUTCROP_SALT = 0x4F555443L
  }
}
