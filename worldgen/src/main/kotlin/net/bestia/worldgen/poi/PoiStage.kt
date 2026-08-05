package net.bestia.worldgen.poi

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.civ.SettlementStage
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max

/**
 * Tuning shared by every entry in [PoiKind].
 *
 * The per-landmark numbers - how likely it is, which biomes it accepts, how tall it stands - are in the
 * catalogue, because they are what distinguishes one landmark from another. What is here is the machinery every
 * one of them uses to find ground: how finely the world is searched, and how far a landmark keeps from the
 * things that would swallow it.
 *
 * **The four clearances all exist for the same reason and it is a concrete one.** `ChunkMaterializer.trunkSite`
 * returns `NaN` - "nothing may stand here" - over paving, under a bridge deck, beneath anything built and over
 * a hole in the ground. A prop offered `NaN` cannot be placed, so a POI whose coordinate lands on one of those
 * is a landmark the world rolled and then silently lost. Keeping clear of them at the world tier is how that is
 * prevented rather than detected; `Invariants.checkPoisBecomeProps` is the detection, and if it ever fires these
 * numbers are what to move.
 */
data class PoiParams(

  /**
   * Metres between the positions the world is searched at.
   *
   * `HistoryParams.candidateStride`'s value and its argument: the search runs once per world over a raster that
   * can be sixteen million cells, and what it is looking for - the right biome, dry ground, room away from
   * everything built - varies at kilometre scale, so sampling every cell would cost a thousand times as much to
   * learn the same thing.
   *
   * It also bounds how *many* places a landmark could have stood, which matters not at all to where it ends up
   * (one is picked at random) and a great deal to whether one can be placed at all. At four kilometres a 128 km
   * world offers about a thousand samples, of which the land, dry and biome filters leave a few hundred.
   */
  val candidateStride: Double = 4_000.0,

  /**
   * Metres above sea level a candidate must stand.
   *
   * A height margin rather than a footprint scan, which is `SpecialSites.isDryGround`'s trade and worth
   * restating because the reasoning is not obvious: `ELEVATION` is a kilometre raster and the ground a prop
   * actually stands on is the detailed column, which swings tens of metres either side of the cell average. No
   * amount of sampling *within* the cell can see that, because every sample returns nearly the same interpolated
   * value. So the guard is a claim about the detail noise's amplitude instead.
   *
   * Lower than a monastery's freeboard would want: a landmark is one object rather than a precinct, so it needs
   * the ground under one point to be dry rather than the ground under eighty metres of garth.
   */
  val freeboard: Double = 12.0,

  /**
   * Metres a POI keeps from any settlement.
   *
   * Comfortably outside the widest `SettlementTier.footprintRadius`, so it clears both the graded ground and the
   * streets on it - and therefore clears `TownStructures.pavingAt`, which is the veto that would otherwise drop
   * the prop. That is also why this stage does not depend on `TownStage`: the streets are inside the footprint
   * this already stands well clear of.
   *
   * There is a second reason it is generous. A landmark in sight of a town is a town's monument, and what these
   * are for is the opposite - something found a long way from anywhere.
   */
  val settlementClearance: Double = 1_500.0,

  /**
   * Metres a POI keeps from anything history left standing or ruined.
   *
   * Sized against the widest of those structures rather than against the narrowest:
   * `HistorySim.WOUND_RADIUS` is 260 m and a fort's is 46, and one number for the family is worth more than
   * eleven tuned separately, since every one of them lays voxels a prop would end up inside.
   */
  val siteClearance: Double = 400.0,

  /**
   * Metres a POI keeps from a cave mouth.
   *
   * The one clearance that guards against a *hole* rather than against a structure, and the failure it prevents
   * is the one a raster could never see: `trunkSite`'s `bracketsGround` refuses any prop whose ground has been
   * carved away beneath it, so a landmark on the lip of an entrance is a landmark in mid-air.
   */
  val caveClearance: Double = 120.0,

  /**
   * Metres a POI keeps from a road.
   *
   * Roads and bridges are both `SettlementStage`'s, and both are hard `NaN` vetoes at the chunk tier - a road
   * because it is ground somebody swept, a deck because a prop placed under one grows through the carriageway.
   * A bridge always lies on a road, so testing the road covers the deck as well.
   *
   * Small compared with the others because a road corridor is a few metres wide and a landmark beside the road
   * is a perfectly good landmark - a waystone is *supposed* to be near one.
   */
  val roadClearance: Double = 40.0,

  /**
   * How many candidate positions are kept per landmark.
   *
   * A shortlist rather than the whole eligible set, and it is a memory bound rather than a tuning knob: a 4096 km
   * world offers a million samples at [candidateStride], and holding every one of them as an object to pick one
   * from would be megabytes to answer a question about a single point.
   *
   * The shortlist is the [maxCandidates] positions with the smallest hash, which makes it a **uniform random
   * sample** of the eligible set rather than a corner of the map - the same trick a lattice scatter plays, read
   * as a selection instead of as a threshold. Its size is therefore how many second chances a landmark gets when
   * its first choice turns out to be inside something; sixty-four is far more than the measured worlds need.
   */
  val maxCandidates: Int = 64
) : Params {

  init {
    require(candidateStride > 0.0) { "candidateStride must be positive, was $candidateStride" }
    require(freeboard >= 0.0) { "freeboard must not be negative, was $freeboard" }
    require(settlementClearance >= 0.0) { "settlementClearance must not be negative, was $settlementClearance" }
    require(siteClearance >= 0.0) { "siteClearance must not be negative, was $siteClearance" }
    require(caveClearance >= 0.0) { "caveClearance must not be negative, was $caveClearance" }
    require(roadClearance >= 0.0) { "roadClearance must not be negative, was $roadClearance" }
    require(maxCandidates > 0) { "maxCandidates must be positive, was $maxCandidates" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    candidateStride = source.double("candidateStride", candidateStride),
    freeboard = source.double("freeboard", freeboard),
    settlementClearance = source.double("settlementClearance", settlementClearance),
    siteClearance = source.double("siteClearance", siteClearance),
    caveClearance = source.double("caveClearance", caveClearance),
    roadClearance = source.double("roadClearance", roadClearance),
    maxCandidates = source.int("maxCandidates", maxCandidates)
  )

  override fun digest() = ParamsDigest()
    .put("candidateStride", candidateStride)
    .put("freeboard", freeboard)
    .put("settlementClearance", settlementClearance)
    .put("siteClearance", siteClearance)
    .put("caveClearance", caveClearance)
    .put("roadClearance", roadClearance)
    .put("maxCandidates", maxCandidates)
}

/**
 * Points of interest: the hand-authored landmarks, rolled once each per world.
 *
 * Read [PoiKind] first - it says what a POI is and why it is neither a scatter nor a built site.
 *
 * ### The roll comes before the search, and that is the whole shape of this stage
 *
 * Every other placement in the pipeline searches first and decides second: `SpecialSites` scores a few hundred
 * hilltops and `HistorySim` then rolls per year against the best of them. This is the other way round. Whether a
 * world holds a lost grave is a property of the *world*, decided by one hash of the seed and the entry's
 * ordinal, and the search only answers where it goes once the answer is yes.
 *
 * That ordering is what makes [PoiKind.chance] mean what it says. Rolled per candidate it would mean "how often
 * a hilltop holds one", and the count would grow with the world's area - a landmark that is unique on a small
 * world and commonplace on a large one. Rolled once, forty-five per cent is forty-five per cent of worlds on
 * every world size.
 *
 * ### Salted by ordinal, never by stream position
 *
 * `GenRng.hashUnit(seed, PRESENCE_SALT, ordinal)` rather than a sequence of draws down the catalogue. Two
 * consequences, both load bearing:
 *
 * - **Appending an entry moves nothing.** A new landmark at the end of [PoiKind] leaves every existing entry's
 *   roll and position untouched, so the catalogue can grow without rewriting every world that already exists.
 * - **A roll that fails costs nothing.** No stream advances, so the entries are genuinely independent rather
 *   than merely looking it.
 *
 * `PRESENCE_SALT` and `PICK_SALT` are separate constants for the reason `HistorySim` keeps eight of them: two of
 * its decisions once shared `HOARD_SALT` and read the same number as each other for the life of the world.
 *
 * ### Two passes of filtering, cheap then expensive
 *
 * The scan applies only what a raster can answer - biome, freeboard, standing water - because it runs at every
 * sampled position on the world. The clearances in [PoiParams] need a distance to every settlement, site, cave
 * mouth and road, so they are asked only of the shortlist, in pick order, until one position passes. A landmark
 * therefore gets [PoiParams.maxCandidates] attempts at finding somewhere nothing else has claimed, and does it
 * without ever measuring a distance for the thousands of positions it will not use.
 */
class PoiStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: PoiParams = PoiParams()
) : Stage {

  override val id = ID

  override val version = 1

  /**
   * The catalogue is folded in beside the params, and it is the more important half.
   *
   * Every number that decides which landmarks a world holds lives in [PoiKind] rather than in [PoiParams], so
   * without `catalogueDigest` a designer could retune the whole feature and move no version number at all.
   * `HistoryStage` folds five catalogues in for the same reason.
   */
  override val paramsVersion get() = GenRng.hash(params.digest().value, PoiKind.catalogueDigest())

  /**
   * Exhaustively what it reads, which is six stages.
   *
   * `GlacialStage` owns `ELEVATION`, `HydrologyStage` owns `WATER_LEVEL`, `BiomeStage` owns `BIOME`, and the
   * three clearances read `CAVE_ENTRANCE`, `SettlementStage`'s settlements and roads, and every site marker
   * history emits. Listing all six rather than the minimum matters because the topological sort breaks ties on
   * the stage *name* - see `VegetationStandStage.ID`.
   *
   * Not `TownStage`, deliberately: its streets are inside a footprint [PoiParams.settlementClearance] already
   * stands well clear of, so depending on it would constrain the schedule to no purpose.
   */
  override val dependencies = listOf(
    GlacialStage.ID,
    HydrologyStage.ID,
    BiomeStage.ID,
    CaveStage.ID,
    SettlementStage.ID,
    HistoryStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Vector(FeatureKind.POI))

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    // The roll first, so a world that holds nothing does no searching at all. See the class KDoc.
    val active = PoiKind.entries.filter {
      GenRng.hashUnit(ctx.seed, PRESENCE_SALT, it.ordinal.toLong()) < it.chance
    }
    if (active.isEmpty()) return StageResult(features = emptyList())

    val shortlists = List(active.size) { Shortlist(params.maxCandidates) }
    scan(ctx, region, active, shortlists)

    val works = Works.read(ctx, region)
    val nextId = FeatureIds.allocator(id)
    val out = ArrayList<VectorFeature>(active.size)

    for ((slot, kind) in active.withIndex()) {
      val at = firstClearPosition(shortlists[slot], works) ?: continue

      out.add(
        PointMarker(
          id = nextId(),
          kind = FeatureKind.POI,
          position = at,
          attributes = StationTable.Builder(stationCount = 1)
            .channel(PoiChannels.KIND) { kind.ordinal.toDouble() }
            .build()
        )
      )
    }

    return StageResult(features = out)
  }

  // --- Finding ground --------------------------------------------------------------------------------

  /**
   * Walks the world on a stride and offers every position that passes the raster tests to each shortlist.
   *
   * One walk for all the active landmarks rather than one per landmark: the three raster tests are the same for
   * every entry and only the biome filter differs, so a walk per entry would re-answer "is this dry land" up to
   * six times per position.
   *
   * The cell key is computed **after** the tests pass, because `GenRng.hash` is a vararg and allocates - see its
   * own note on that. Each landmark's own key is then walked off it with `mix64`, which is `VegetationScatter`'s
   * idiom and keeps the per-position cost at one allocation however long the catalogue grows.
   */
  private fun scan(
    ctx: GenContext,
    region: CellRegion,
    active: List<PoiKind>,
    shortlists: List<Shortlist>
  ) {
    val metres = region.resolution.metresPerCell
    val stride = max(1, (params.candidateStride / metres).toInt())
    val elevation = ctx.layers.float(LayerId.ELEVATION)
    val waterLevel = ctx.layers.float(LayerId.WATER_LEVEL)
    val biome = ctx.layers.int(LayerId.BIOME)
    val seaLevel = ctx.config.seaLevel
    val pickBase = GenRng.hash(ctx.seed, PICK_SALT)

    var y = 0
    while (y < region.height) {
      var x = 0
      while (x < region.width) {
        val cellX = region.minX + x
        val cellY = region.minY + y
        // Advanced here rather than at the end of the body, because everything below is a `continue`.
        x += stride

        val here = Biome.entries[biome[cellX, cellY]]
        if (here.isWater) continue

        val worldX = (cellX + 0.5) * metres
        val worldY = (cellY + 0.5) * metres
        if (elevation.sampleBilinear(worldX, worldY) - seaLevel < params.freeboard) continue
        if (!isClearOfStandingWater(waterLevel, cellX, cellY)) continue

        val cellKey = GenRng.hash(pickBase, cellX.toLong(), cellY.toLong())
        // Indexed rather than `withIndex`, which allocates an `IndexedValue` per step: this is the innermost
        // loop of a walk over up to a million positions, times the length of the catalogue.
        for (slot in active.indices) {
          val kind = active[slot]
          if (!kind.allows(here)) continue
          // Off the cell's own key with `mix64`, so a landmark's draw depends on its **ordinal** and not on its
          // position in `active`. That is what lets a failed roll cost nothing and an appended entry disturb no
          // existing one - see the class KDoc.
          shortlists[slot].offer(GenRng.mix64(cellKey + kind.ordinal), worldX, worldY)
        }
      }
      y += stride
    }
  }

  /**
   * Whether this cell and all eight of its neighbours are free of standing water.
   *
   * `SpecialSites.isClearOfStandingWater`'s test, and its argument holds here unchanged: [PoiParams.freeboard]
   * compares against *sea* level, so a lake sitting at five hundred metres is five hundred metres of freeboard
   * and still wet. `WATER_LEVEL` is NaN exactly where there is no standing water.
   *
   * The full ring rather than the cell, because a shoreline crosses a cell: the position's own cell can be dry
   * while the water begins forty metres away.
   */
  private fun isClearOfStandingWater(waterLevel: FloatLayer, cellX: Int, cellY: Int): Boolean {
    for (dy in -1..1) {
      for (dx in -1..1) {
        if (!waterLevel[cellX + dx, cellY + dy].isNaN()) return false
      }
    }
    return true
  }

  /** The first shortlisted position nothing else has claimed, or null if every one of them is taken. */
  private fun firstClearPosition(shortlist: Shortlist, works: Works): Vec2d? {
    for (i in 0 until shortlist.size) {
      val at = Vec2d(shortlist.xAt(i), shortlist.yAt(i))
      if (works.isClear(at, params)) return at
    }
    return null
  }

  /**
   * Everything already standing on the ground that a landmark has to keep away from.
   *
   * Read once per world and held as four flat lists, because the shortlist walk asks about the same set for
   * every landmark and re-querying the feature store per position would be the expensive half of this stage.
   */
  private class Works(
    val settlements: List<Vec2d>,
    val sites: List<Vec2d>,
    val caveMouths: List<Vec2d>,
    val roads: List<PolylineFeature>
  ) {

    fun isClear(at: Vec2d, params: PoiParams): Boolean {
      if (settlements.any { it.distanceTo(at) < params.settlementClearance }) return false
      if (sites.any { it.distanceTo(at) < params.siteClearance }) return false
      if (caveMouths.any { it.distanceTo(at) < params.caveClearance }) return false
      // Last because it is the only test that is not a point distance: a road is a polyline and the projection
      // walks its segments, so it costs the most and is asked of the fewest positions.
      if (roads.any { it.centerline.project(at).distance < params.roadClearance }) return false
      return true
    }

    companion object {

      /**
       * Every site kind history emits that lays voxels on the ground.
       *
       * All of them rather than the ones with the widest structures, because the cost is a distance per marker
       * and the failure of missing one is a landmark inside a fort. `CAVE_HOARD` is deliberately absent - it is
       * underground, and a hoard beneath a standing stone is a coincidence rather than a collision.
       */
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
        FeatureKind.WOUND
      )

      fun read(ctx: GenContext, region: CellRegion): Works {
        val all = ctx.features.query(region.toWorld())
        val settlements = ArrayList<Vec2d>()
        val sites = ArrayList<Vec2d>()
        val caveMouths = ArrayList<Vec2d>()
        val roads = ArrayList<PolylineFeature>()

        for (feature in all) {
          when {
            feature.kind == FeatureKind.SETTLEMENT ->
              (feature as? PointMarker)?.let { settlements.add(it.position) }

            feature.kind in SITE_KINDS ->
              (feature as? PointMarker)?.let { sites.add(it.position) }

            feature.kind == FeatureKind.CAVE_ENTRANCE ->
              (feature as? PointMarker)?.let { caveMouths.add(it.position) }

            // A bridge always lies on a road, so the road covers the deck as well - see
            // `PoiParams.roadClearance`.
            feature.kind == FeatureKind.ROAD ->
              (feature as? PolylineFeature)?.let { roads.add(it) }
          }
        }

        return Works(settlements, sites, caveMouths, roads)
      }
    }
  }

  /**
   * The best [limit] candidate positions by hash, kept without materialising the rest.
   *
   * Parallel arrays and an insertion into a sorted prefix, which is the right shape for the traffic: almost
   * every offer is rejected by one comparison against the current worst, and the few that are not shift a
   * handful of slots. A heap would be the same asymptotics with more code, and a full list then sorted would be
   * the memory this exists to avoid.
   *
   * Kept ascending, so index order **is** pick order.
   */
  private class Shortlist(private val limit: Int) {

    private val keys = LongArray(limit)
    private val xs = DoubleArray(limit)
    private val ys = DoubleArray(limit)

    var size = 0
      private set

    fun offer(key: Long, x: Double, y: Double) {
      if (size == limit && key >= keys[limit - 1]) return

      var at = if (size < limit) size else limit - 1
      while (at > 0 && keys[at - 1] > key) {
        keys[at] = keys[at - 1]
        xs[at] = xs[at - 1]
        ys[at] = ys[at - 1]
        at--
      }

      keys[at] = key
      xs[at] = x
      ys[at] = y
      if (size < limit) size++
    }

    fun xAt(i: Int) = xs[i]

    fun yAt(i: Int) = ys[i]
  }

  companion object {

    val ID = StageId("poi")

    /** Whether a world holds a given landmark at all. */
    private const val PRESENCE_SALT = 0x504F495F484153L

    /** Which of the places it could have stood it does. Its own constant; see the class KDoc. */
    private const val PICK_SALT = 0x504F495F50434BL
  }
}
