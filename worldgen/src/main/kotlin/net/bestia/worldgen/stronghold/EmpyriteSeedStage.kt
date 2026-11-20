package net.bestia.worldgen.stronghold

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.civ.SettlementChannels
import net.bestia.worldgen.civ.SettlementSpawnPoints
import net.bestia.worldgen.civ.SettlementStage
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
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.history.Names
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.poi.PoiStage
import net.bestia.worldgen.resource.DepositChannels
import net.bestia.worldgen.resource.MinableOre
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.resource.ResourceType
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * The pool of Empyrite Seeds: the only spots where a guild can found a Stronghold.
 *
 * A seed stands just outside the reach of rich ore, so the guild that holds it can mine around its own grounds.
 * The pool is ranked by that worth, and the zone wakes seeds in rank order as more guilds earn Official Guild
 * Approval. See the Guild Strongholds page in bestia-docs.
 */
class EmpyriteSeedStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: EmpyriteSeedParams = EmpyriteSeedParams()
) : Stage {

  override val id = ID

  override val version = 1

  /** The ore's worth is part of the ranking, so its value table moves the version too. */
  override val paramsVersion get() = GenRng.hash(params.digest().value, ResourceType.catalogueDigest())

  override val dependencies = listOf(
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID,
    ResourceStage.ID,
    CaveStage.ID,
    SettlementStage.ID,
    HistoryStage.ID,
    PoiStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.EMPYRITE_SEED))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val ore = Ore.read(ctx, region, params.resourceReach)
    if (ore.isEmpty()) return StageResult(features = emptyList())

    val works = Works.read(ctx, region)
    val ground = Ground(ctx, region, params)
    val ranked = candidatesAround(ore, ctx.seed)
      .filter { ground.isDryAndInside(it) && ore.leavesGroundsFree(it, params.groundsRadius) }
      .filter { works.isClearOfSettlements(it, params) }
      .map { Candidate(it, ore.worthAround(it, params.resourceReach), GenRng.hash(ctx.seed, PICK_SALT, it.x.toLong(), it.y.toLong())) }
      .sortedWith(compareByDescending<Candidate> { it.worth }.thenBy { it.tieBreak })

    val chosen = ArrayList<Candidate>()
    for (candidate in ranked) {
      if (chosen.size == params.poolSize) break
      if (chosen.any { it.at.distanceTo(candidate.at) < params.seedSeparation }) continue
      // Last, because these walk every site and every road segment.
      if (!works.isClearOfBuiltThings(candidate.at, params)) continue
      chosen.add(candidate)
    }

    return StageResult(features = markersFor(chosen, ctx, works))
  }

  /** Positions on a ring around every deposit, each ring turned by its own hash so they do not line up. */
  private fun candidatesAround(ore: Ore, seed: Long): List<Vec2d> {
    val out = ArrayList<Vec2d>(ore.deposits.size * params.ringBearings)
    for (deposit in ore.deposits) {
      val turn = GenRng.hashUnit(seed, RING_SALT, deposit.id)
      for (k in 0 until params.ringBearings) {
        val angle = 2.0 * PI * (k + turn) / params.ringBearings
        out.add(Vec2d(deposit.at.x + params.ringRadius * cos(angle), deposit.at.y + params.ringRadius * sin(angle)))
      }
    }
    return out
  }

  private fun markersFor(chosen: List<Candidate>, ctx: GenContext, works: Works): List<PointMarker> {
    val takenNames = works.settlementNames(ctx).toHashSet()
    val nextId = FeatureIds.allocator(id)

    return chosen.mapIndexed { rank, candidate ->
      val people = works.nearestSettlement(candidate.at)
      val nameSeed = uniqueNameSeed(ctx.seed, rank, people.culture, takenNames)
      PointMarker(
        id = nextId(),
        kind = FeatureKind.EMPYRITE_SEED,
        position = candidate.at,
        attributes = StationTable.Builder(stationCount = 1)
          .channel(EmpyriteSeedChannels.RANK) { rank.toDouble() }
          .channel(EmpyriteSeedChannels.WORTH) { candidate.worth }
          .channel(EmpyriteSeedChannels.NAME_SEED) { nameSeed.toDouble() }
          .channel(EmpyriteSeedChannels.CULTURE) { people.culture.toDouble() }
          .channel(EmpyriteSeedChannels.SETTLEMENT) { people.standingIndex.toDouble() }
          .build()
      )
    }
  }

  /** Every seed has a name no settlement and no other seed carries, so a player can tell them apart. */
  private fun uniqueNameSeed(worldSeed: Long, rank: Int, culture: Int, taken: MutableSet<String>): Long {
    for (attempt in 0 until MAX_NAME_ATTEMPTS) {
      // A marker channel holds a Double, so the name is checked on the seed that comes back out of it.
      val nameSeed = Names.seedOf(worldSeed, NAME_SALT, rank.toLong(), attempt.toLong()).toDouble().toLong()
      if (taken.add(Names.place(nameSeed, culture))) return nameSeed
    }
    error("No free name for Empyrite Seed $rank after $MAX_NAME_ATTEMPTS attempts")
  }

  private data class Candidate(val at: Vec2d, val worth: Double, val tieBreak: Long)

  private class Deposit(val id: Long, val at: Vec2d, val radius: Double, val worth: Double)

  /** The world's mineable ore, bucketed on a grid so a candidate only looks at the deposits near it. */
  private class Ore(val deposits: List<Deposit>, private val cellSize: Double) {

    private val buckets = HashMap<Long, MutableList<Deposit>>()

    init {
      for (deposit in deposits) buckets.getOrPut(keyOf(deposit.at.x, deposit.at.y)) { ArrayList() }.add(deposit)
    }

    fun isEmpty() = deposits.isEmpty()

    fun leavesGroundsFree(at: Vec2d, groundsRadius: Double): Boolean {
      return near(at).none { it.at.distanceTo(at) - it.radius < groundsRadius }
    }

    fun worthAround(at: Vec2d, reach: Double): Double {
      return near(at).filter { it.at.distanceTo(at) <= reach }.sumOf { it.worth }
    }

    /** Every deposit within one cell of [at], which covers any reach up to [cellSize]. */
    private fun near(at: Vec2d): Sequence<Deposit> {
      val cx = floor(at.x / cellSize).toLong()
      val cy = floor(at.y / cellSize).toLong()
      return sequence {
        for (dy in -1L..1L) for (dx in -1L..1L) buckets[pack(cx + dx, cy + dy)]?.let { yieldAll(it) }
      }
    }

    private fun keyOf(x: Double, y: Double) = pack(floor(x / cellSize).toLong(), floor(y / cellSize).toLong())

    private fun pack(cx: Long, cy: Long) = (cx shl 32) xor (cy and 0xFFFF_FFFFL)

    companion object {

      fun read(ctx: GenContext, region: CellRegion, reach: Double): Ore {
        val deposits = ctx.features.query(region.toWorld())
          .asSequence()
          .filter { it.kind == FeatureKind.ORE_DEPOSIT }
          .filterIsInstance<PointMarker>()
          .mapNotNull { marker ->
            val type = ResourceType.entries[marker.attribute(DepositChannels.TYPE).toInt()]
            // Only ore a guild can dig up; timber, furs and fish carry nominal numbers.
            if (MinableOre.of(type) == null) return@mapNotNull null
            Deposit(
              id = marker.id.value,
              at = marker.position,
              radius = marker.attribute(DepositChannels.RADIUS),
              worth = type.value * marker.attribute(DepositChannels.RICHNESS)
            )
          }
          .sortedBy { it.id }
          .toList()
        return Ore(deposits, reach)
      }
    }
  }

  /** What the rasters can answer: inside the world with room for the grounds, on dry land. */
  private class Ground(ctx: GenContext, region: CellRegion, private val params: EmpyriteSeedParams) {

    private val metres = region.resolution.metresPerCell
    private val elevation = ctx.layers.float(LayerId.ELEVATION)
    private val waterLevel = ctx.layers.float(LayerId.WATER_LEVEL)
    private val biome = ctx.layers.int(LayerId.BIOME)
    private val seaLevel = ctx.config.seaLevel
    private val inner: Aabb = region.toWorld().let {
      val margin = params.groundsRadius
      Aabb(it.minX + margin, it.minY + margin, it.maxX - margin, it.maxY - margin)
    }

    fun isDryAndInside(at: Vec2d): Boolean {
      if (at.x !in inner.minX..inner.maxX || at.y !in inner.minY..inner.maxY) return false
      val cellX = floor(at.x / metres).toInt()
      val cellY = floor(at.y / metres).toInt()
      if (Biome.entries[biome[cellX, cellY]].isWater) return false
      if (elevation.sampleBilinear(at.x, at.y) - seaLevel < params.freeboard) return false
      // The whole ring of cells: a shoreline can cross the seed's own cell. See `PoiStage`.
      for (dy in -1..1) for (dx in -1..1) if (!waterLevel[cellX + dx, cellY + dy].isNaN()) return false
      return true
    }
  }

  private class People(val culture: Int, val standingIndex: Int)

  /** Everything already on the ground that a seed keeps away from. */
  private class Works(
    private val settlements: List<PointMarker>,
    private val history: Map<Int, PointMarker>,
    private val sites: List<Vec2d>,
    private val caveMouths: List<Vec2d>,
    private val pois: List<Vec2d>,
    private val roads: List<PolylineFeature>
  ) {

    /** The settlements new masters arrive at, which carry a ward stone. The same answer `SpawnerStage` uses. */
    private val homes: List<Vec2d> by lazy {
      val standing = settlements.filter { isStanding(indexOf(it)) }.associateBy { indexOf(it) }
      val populations = standing.keys.associateWith { history.getValue(it).attribute(HistoryChannels.POPULATION).toInt() }
      SettlementSpawnPoints.homeCandidateIndices(populations).mapNotNull { standing[it]?.position }
    }

    fun isClearOfSettlements(at: Vec2d, params: EmpyriteSeedParams): Boolean {
      if (settlements.any { it.position.distanceTo(at) < params.settlementClearance }) return false
      return homes.none { it.distanceTo(at) < params.homeClearance }
    }

    fun isClearOfBuiltThings(at: Vec2d, params: EmpyriteSeedParams): Boolean {
      if (sites.any { it.distanceTo(at) < params.siteClearance }) return false
      if (caveMouths.any { it.distanceTo(at) < params.caveClearance }) return false
      if (pois.any { it.distanceTo(at) < params.poiClearance }) return false
      return roads.none { it.centerline.project(at).distance < params.roadClearance }
    }

    /** The people who live nearest name the seed. Where nobody lives any more, the nearest ruin's people do. */
    fun nearestSettlement(at: Vec2d): People {
      val standing = settlements.filter { isStanding(indexOf(it)) }.minByOrNull { it.position.distanceTo(at) }
      val namer = standing ?: settlements.minByOrNull { it.position.distanceTo(at) } ?: return People(0, -1)
      val culture = history[indexOf(namer)]?.attribute(HistoryChannels.CULTURE)?.toInt()
        ?: namer.attribute(SettlementChannels.CULTURE).toInt()
      return People(culture.coerceAtLeast(0), standing?.let { indexOf(it) } ?: -1)
    }

    fun settlementNames(ctx: GenContext): List<String> {
      return ctx.chronicle().settlements.map { Names.place(it.nameSeed, it.nameCulture.coerceAtLeast(0)) }
    }

    private fun isStanding(index: Int): Boolean {
      val past = history[index] ?: return false
      return past.attribute(HistoryChannels.FOUNDED_YEAR).toInt() != 0 &&
        past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() == 0
    }

    private fun indexOf(settlement: PointMarker) = settlement.attribute(SettlementChannels.INDEX).toInt()

    companion object {

      /** The history sites that lay voxels on the ground, as `PoiStage` lists them. */
      private val SITE_KINDS = setOf(
        FeatureKind.RUIN,
        FeatureKind.ASH_RUIN,
        FeatureKind.BATTLEFIELD,
        FeatureKind.MONUMENT,
        FeatureKind.TOMB,
        FeatureKind.MINE,
        FeatureKind.MONASTERY,
        FeatureKind.FORT,
        FeatureKind.LIGHTHOUSE,
        FeatureKind.SHRINE,
        FeatureKind.SANCTUARY,
        FeatureKind.SEAL,
        FeatureKind.LAIR,
        FeatureKind.WOUND
      )

      fun read(ctx: GenContext, region: CellRegion): Works {
        val settlements = ArrayList<PointMarker>()
        val history = HashMap<Int, PointMarker>()
        val sites = ArrayList<Vec2d>()
        val caveMouths = ArrayList<Vec2d>()
        val pois = ArrayList<Vec2d>()
        val roads = ArrayList<PolylineFeature>()

        for (feature in ctx.features.query(region.toWorld())) {
          when (feature.kind) {
            FeatureKind.SETTLEMENT -> (feature as? PointMarker)?.let { settlements.add(it) }
            FeatureKind.SETTLEMENT_HISTORY ->
              (feature as? PointMarker)?.let { history[it.attribute(HistoryChannels.INDEX).toInt()] = it }
            FeatureKind.CAVE_ENTRANCE -> (feature as? PointMarker)?.let { caveMouths.add(it.position) }
            FeatureKind.POI -> (feature as? PointMarker)?.let { pois.add(it.position) }
            FeatureKind.ROAD -> (feature as? PolylineFeature)?.let { roads.add(it) }
            in SITE_KINDS -> (feature as? PointMarker)?.let { sites.add(it.position) }
            else -> {}
          }
        }

        return Works(settlements, history, sites, caveMouths, pois, roads)
      }
    }

  }

  companion object {

    val ID = StageId("empyrite_seeds")

    private const val MAX_NAME_ATTEMPTS = 64

    private const val RING_SALT = 0x454D505F52494EL
    private const val PICK_SALT = 0x454D505F50434BL
    private const val NAME_SALT = 0x454D505F4E414DL
  }
}
