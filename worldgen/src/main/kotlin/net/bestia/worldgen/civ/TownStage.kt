package net.bestia.worldgen.civ

import net.bestia.worldgen.climate.ClimateStage
import net.bestia.worldgen.climate.Winds
import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.FeatureIds
import net.bestia.worldgen.core.FloatLayer
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.GenRng
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.core.BaseHeightField
import net.bestia.worldgen.geo.DetailParams
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.FootprintFeature
import net.bestia.worldgen.vector.Intersections
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import net.bestia.worldgen.voxel.BlockType
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Tuning for [TownStage]. */
data class TownParams(

  /**
   * The settlement stage's grading limits.
   *
   * Held rather than duplicated for the same reason [SettlementParams] holds [HabitabilityParams]: this
   * stage has to predict what the grading feature will do to the ground in order to decide a building's
   * floor elevation, and a second copy of the two numbers would eventually disagree with the first. Both
   * default, so both are right unless a caller overrides one and not the other.
   */
  val grading: SettlementParams = SettlementParams(),

  val streets: StreetParams = StreetParams(),

  /**
   * The chunk tier's detail noise, because this stage samples the surface a chunk will produce.
   *
   * Held for the same reason as [grading], and with a sharper edge: `WorldGround` builds its own
   * [net.bestia.worldgen.geo.WorldHeightField] to predict the ground a building will stand on, and if its
   * detail parameters differ from the ones `StandardWorld.assemble` gives the chunk tier, then **every building
   * in the world sits slightly off the ground**. That agreement used to rest on both sides defaulting; now that
   * the chunk tier's params are reachable, it rests on this being forwarded.
   */
  val detail: DetailParams = DetailParams(),

  /**
   * People per hectare of built-up area.
   *
   * What turns a population into a radius, and therefore the single number that decides how big every town
   * in the world is.
   *
   * It is **measured, not assumed**, and has to be re-measured whenever the plot dimensions change: it started
   * at a hundred and forty - a plausible-sounding density for a dense pre-industrial town - and left every
   * settlement wanting forty percent more buildings than its own streets had room for, which is the discrepancy
   * the `town` view's "wanted versus built" line exists to show. Eighty-five was the measured answer for nine
   * metres of frontage; fifty-eight is the measured answer for the twelve and a half below, which fits fewer
   * plots on the same length of street.
   */
  val peoplePerHectare: Double = 58.0,

  /** Residents per building. Five and a half is a household plus the odd lodger. */
  val peoplePerBuilding: Double = 5.5,

  /**
   * Metres of street frontage per plot, and metres of depth back from it.
   *
   * Three multipliers stand between these and a building's actual size - `LOT_GAP` leaves a gap between
   * neighbouring plots, `FOOTPRINT_FILL` leaves a yard inside one, and the per-function multiplier in
   * `TownBuildings.footprintFor` - so a nine-metre frontage produced a house **6.35 m** wide. Which is a shed:
   * a room and a half, with no wall thickness allowed for.
   *
   * Twelve and a half brings that to 10.35 m by 16.2 m, or 168 m² over however many storeys the culture builds -
   * a house a household plausibly lives in. It costs about a quarter of the plots per length of street, which is
   * what [peoplePerHectare] absorbs, and the town simply comes out proportionally wider.
   */
  val lotFrontage: Double = 12.5,
  val lotDepth: Double = 18.0,

  /** Metres between the street centreline and the front of a plot. */
  val setback: Double = 4.0,

  /**
   * Ceiling on buildings per settlement.
   *
   * A cap, and an honest one: a city of forty thousand wants seven thousand buildings, and a world of
   * three hundred settlements at that rate is a couple of million features. What the cap keeps is the
   * *centre* - lots are assigned in descending land value, so what survives is the part a player walks
   * through, and what is dropped is the outer residential ring. The count that was wanted and the count
   * that was built are both reported by the `town` tool, because a silently truncated town reads as a small
   * one.
   */
  val maxBuildingsPerSettlement: Int = 1_200,

  /** Ground slope above which nothing is built, as a gradient. About one in four. */
  val maxBuildableSlope: Double = 0.26,

  /** Metres of clearance kept either side of a river channel's centreline, beyond its own half-width. */
  val riverClearance: Double = 6.0,

  /** Wall height and thickness in metres. */
  val wallHeight: Double = 7.5,
  val wallThickness: Double = 2.2,

  /** Metres of opening at a gate. */
  val gateWidth: Double = 7.0,

  /** Station spacing along a street centreline, in metres. */
  val streetSpacing: Double = 8.0
) : Params {

  init {
    require(peoplePerHectare > 0.0) { "peoplePerHectare must be positive, was $peoplePerHectare" }
    require(peoplePerBuilding > 0.0) { "peoplePerBuilding must be positive, was $peoplePerBuilding" }
    require(lotFrontage > 0.0) { "lotFrontage must be positive, was $lotFrontage" }
    require(lotDepth > 0.0) { "lotDepth must be positive, was $lotDepth" }
    require(setback >= 0.0) { "setback must not be negative, was $setback" }
    require(maxBuildingsPerSettlement >= 0) {
      "maxBuildingsPerSettlement must not be negative, was $maxBuildingsPerSettlement"
    }
    require(maxBuildableSlope > 0.0) { "maxBuildableSlope must be positive, was $maxBuildableSlope" }
    require(riverClearance >= 0.0) { "riverClearance must not be negative, was $riverClearance" }
    require(wallHeight > 0.0) { "wallHeight must be positive, was $wallHeight" }
    require(wallThickness > 0.0) { "wallThickness must be positive, was $wallThickness" }
    require(gateWidth > 0.0) { "gateWidth must be positive, was $gateWidth" }
    // Stations are laid along a centreline at this spacing; zero would be an unbounded station table on the
    // first street, which shows up as a hang rather than as a wrong town.
    require(streetSpacing > 0.0) { "streetSpacing must be positive, was $streetSpacing" }
  }

  override fun digest() = ParamsDigest()
    .nested("grading", grading.digest().value)
    .nested("streets", streets.digest().value)
    .nested("detail", detail.digest().value)
    .put("peoplePerHectare", peoplePerHectare)
    .put("peoplePerBuilding", peoplePerBuilding)
    .put("lotFrontage", lotFrontage)
    .put("lotDepth", lotDepth)
    .put("setback", setback)
    .put("maxBuildingsPerSettlement", maxBuildingsPerSettlement)
    .put("maxBuildableSlope", maxBuildableSlope)
    .put("riverClearance", riverClearance)
    .put("wallHeight", wallHeight)
    .put("wallThickness", wallThickness)
    .put("gateWidth", gateWidth)
    .put("streetSpacing", streetSpacing)
}

/**
 * Step 8: town layout and buildings.
 *
 * For every settlement history left standing: a street network grown from the roads that arrive, blocks
 * from the network's faces, street-fronting plots around each block, a function per plot from land value,
 * and a building on each. Walls where history says the place was attacked, with gates where the main
 * streets cross the circuit.
 *
 * ### What goes in the vector tier and what does not
 *
 * Streets are [PolylineFeature]s - a road with a narrower cross-section, so they reuse
 * [LinearFeatures.road] and add no geometry code, which is the same payoff roads got from rivers.
 *
 * Buildings are [FootprintFeature]s, the oriented rectangle added for this stage. Each one *flattens the
 * ground it covers* and carries the attributes the materialiser needs, in one feature: that is the
 * document's "soft deformation applied to the heightfield before stratification, so buildings sit on graded
 * ground rather than floating or clipping", and doing both jobs in one feature is what makes it impossible
 * for the pad and the walls to disagree about where the building is.
 *
 * Walls are geometry-only markers, because a wall is a structure standing *on* the ground and a heightfield
 * has one height per column - the same reason a bridge deck is blocks rather than terrain. The materialiser
 * lays them from the base elevation the stations record.
 *
 * ### Still not a polygon
 *
 * The design stores a settlement as a polygon boundary with a street graph inside it. There is no polygon
 * type, so the boundary is still the disc the grading feature already was, and the street graph is stamped
 * rather than stored as a graph. What that costs is a query - "is this position inside the town" is a
 * distance test against the disc rather than a point-in-polygon against the built edge - and nothing yet
 * asks it.
 */
class TownStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: TownParams = TownParams()
) : Stage {

  override val id = ID
  // 2: plots and footprints grown so a house is a house; per-function sizes; a shallower roof on a wide span.
  //    The roof lives in `voxel/TownStructures` and has no version of its own, so it rides on this one.
  // 3: a lot whose footprint the pad cannot level is skipped, and the next-best lot takes the building.
  // 4: a town wall stops at the waterfront instead of running out to sea.
  // 5: towns are laid out in parallel, so each takes its ids from its own block of the ordinal space.
  //    No town is shaped differently, but every id in the stage moves, and ids tie-break stamp order.
  override val version = 5

  override val paramsVersion get() = GenRng.hash(params.digest().value, Culture.catalogueDigest(), SettlementTier.catalogueDigest())
  override val dependencies = listOf(
    ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, SettlementStage.ID, HistoryStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.STREET),
    StageOutput.Vector(FeatureKind.BUILDING),
    StageOutput.Vector(FeatureKind.TOWN_WALL),
    StageOutput.Vector(FeatureKind.GATE)
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val towns = TownReader.read(ctx, region)
    if (towns.isEmpty()) return StageResult.EMPTY

    val world = WorldGround(ctx, region, params)
    val streamBase = GenRng.hash(ctx.seed, id.hash, version.toLong())

    /*
     * One town at a time, on as many cores as there are.
     *
     * This is the most expensive thing in the pipeline - two and a half seconds of a ten second reference
     * world, more than erosion - and it is also the most trivially separable, because a town is laid out
     * against the finished world and against nothing its neighbours did. `WorldGround` holds only layers
     * and two prebuilt feature lists, `StreetPlanner` and `LotPlanner` are stateless, and the roll is
     * already salted per town, so there is no shared mutable state to find.
     *
     * The one thing that was shared is the id allocator, and a shared counter here would have made every
     * feature id in the world depend on which town finished first. Hence the per-town block: see
     * [FeatureIds.blockAllocator].
     */
    val perTown = Timings.measure("towns.layOut") {
      Parallel.map(towns.size) { i ->
        layOut(towns[i], world, ctx.config, streamBase, FeatureIds.blockAllocator(id, i))
      }
    }

    // Flattened in town order, so the store sees exactly the sequence the serial loop gave it.
    val out = ArrayList<VectorFeature>()
    for (features in perTown) out.addAll(features)

    return StageResult(features = out)
  }

  /** Everything one settlement contributes: its streets, its buildings, and its walls if it has any. */
  private fun layOut(
    town: TownReader.Town,
    world: WorldGround,
    config: WorldConfig,
    streamBase: Long,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val roll = townRoll(streamBase, town.index)
    val builtRadius = builtRadiusFor(town.population, town.tier)
    if (builtRadius < params.streets.segmentLength) return emptyList()

    val frame = TownFrame(
      centre = town.position,
      builtRadius = builtRadius,
      groundAt = { world.gradedGround(it, town.siteElevation) },
      buildable = { world.buildable(it, town) },
      approaches = world.approachesTo(town, builtRadius)
    )

    val graph = StreetPlanner.plan(frame, town.culture.layout, roll, params.streets)
    if (graph.edges.isEmpty()) return emptyList()

    val lots = LotPlanner.subdivide(
      graph, frame, params.lotFrontage, params.lotDepth, params.setback
    )
    if (lots.isEmpty()) return emptyList()

    val zoning = Zoning(
      frame = frame,
      population = town.population,
      wealth = town.wealth,
      cultureIndex = town.cultureIndex,
      coastal = town.coastal,
      downwind = world.downwindAt(town.position, config),
      downstream = world.downstreamAt(town.position),
      roll = roll
    )

    // Wanted, then capped. Descending land value, so what a cap drops is the outer residential ring and
    // what it keeps is the centre - which is the part anybody stands in.
    val wanted = min(lots.size, max(1, (town.population / params.peoplePerBuilding).toInt()))
    val functions = zoning.assign(lots)
    val limit = min(wanted, params.maxBuildingsPerSettlement)

    // Walked in value order and *filled* to the limit rather than sliced at it, so that a lot rejected below
    // costs the town a worse lot rather than a building. Slicing first and filtering after would shrink every
    // town by however many bad sites its best lots happened to contain.
    val placed = ArrayList<Building>(limit)
    for (index in lots.indices.sortedWith(compareByDescending<Int> { zoning.valueOf(lots[it]) }.thenBy { it })) {
      if (placed.size >= limit) break

      // `index` is also the RNG salt for storeys, materials and roof, so it has to stay the lot's own index
      // and not a running count - renumbering here would change every building in the town.
      val building = zoning.buildingFor(lots[index], functions[index], index)
      if (standsLevel(building, world, town)) placed.add(building)
    }

    val out = ArrayList<VectorFeature>(placed.size + graph.edges.size / 4 + 8)

    for ((rank, chain) in graph.chains().map { it.second to it.first }) {
      out.addAll(streetFeatures(nextId, chain, rank, frame, world))
    }

    for (building in placed) {
      out.add(buildingFeature(nextId(), building, town.index))
    }

    if (town.wallYear != 0) {
      out.addAll(fortify(town, frame, graph, world, nextId))
    }

    return out
  }

  /**
   * Whether the pad can actually level this building's ground, at all four corners.
   *
   * The site test a lot goes through is [WorldGround.buildable], which reads the slope off the **kilometre
   * raster**. A building is eighteen metres long. A kilometre-averaged slope cannot see a scarp shorter than
   * itself, so a lot can pass that test and still have five metres of relief across its own footprint - and
   * the pad, being a terrace with a 2.5 m cut and a 1.5 m fill rather than a flat replace, then leaves the
   * floor standing metres clear of the ground at the back corners. What the materialiser does with that is
   * build a plinth, and a plinth that tall is a house on stilts.
   *
   * So the check has to be made at the building's own scale, which means after the building exists and its
   * floor is known. Predicting the residual rather than measuring it afterwards is what lets the caller fall
   * through to the next lot instead of losing the building.
   */
  private fun standsLevel(building: Building, world: WorldGround, town: TownReader.Town): Boolean {
    val floor = building.floorElevation
    val along = building.bearing * building.halfLength
    val across = building.bearing.perpendicular() * building.halfWidth

    for (corner in CORNER_SIGNS) {
      val at = building.centre + along * corner.first + across * corner.second
      val ground = world.gradingFaded(at, town)
      val padded = when {
        ground > floor -> max(floor, ground - PAD_MAX_CUT)
        ground < floor -> min(floor, ground + PAD_MAX_FILL)
        else -> floor
      }
      if (abs(padded - floor) > PAD_MAX_RESIDUAL) return false
    }

    return true
  }

  /**
   * Radius of the built-up area, from population and density.
   *
   * From the *present* population rather than from the tier, which is the whole reason history runs first: a
   * city that was sacked twice and never recovered is physically smaller than one that was not, and reading
   * the radius off the tier would make them the same size and leave the sacking with no consequence anybody
   * can see. Capped by the graded footprint, because beyond that the ground is not level and the buildings
   * would be standing on a hillside.
   *
   * ### The cap has to reserve a lot, not a percentage
   *
   * This is a radius for the *street network*, and a plot hangs off a street: its centre sits
   * `setback + lotDepth / 2` beyond the kerb and its far edge a further `lotDepth / 2`. So a share of the
   * footprint radius is the wrong instrument, because the margin it leaves scales with the settlement while
   * the thing that has to fit in that margin does not. At 95% a city keeps 45 m of headroom for a 22 m plot
   * and a **village keeps 9.5 m**, which is not enough - so a village whose streets happened to reach the cap
   * put its outermost buildings outside the ground it had graded, standing on a hillside, which is the same
   * plinth problem in a smaller hat. A hamlet had 4.5 m and was worse.
   *
   * Subtracting the plot's full reach instead makes the property true by construction and independent of tier:
   * every lot, not merely its centre, lies inside the graded disc. `Invariants` checks the centre, which is
   * the weaker claim; this keeps the stronger one.
   */
  private fun builtRadiusFor(population: Int, tier: SettlementTier): Double {
    val hectares = population / params.peoplePerHectare
    val radius = sqrt(max(hectares, 0.05) * SQUARE_METRES_PER_HECTARE / PI)

    // Floored above zero so a hamlet on a tight footprint still gets one ring of streets rather than none.
    val usable = (tier.footprintRadius * FOOTPRINT_SHARE - (params.setback + params.lotDepth))
      .coerceAtLeast(tier.footprintRadius * MIN_BUILT_SHARE)

    return min(radius, usable)
  }

  // --- Features -------------------------------------------------------------------------------------

  /**
   * A street chain, as one feature per stretch of it over dry land.
   *
   * **The segment filter is not enough, and the reason is sampling density.** `StreetNetwork` keeps a segment
   * only if it is buildable at five points along it, which a narrow inlet fits between - and the street that is
   * finally emitted is resampled at `streetSpacing`, so it has many more vertices than were ever tested. The
   * sweep's `nothing is built in water` checks every one of them, and found a street across water on 1 seed in
   * 200 at 192 cells. Latent, like the town wall beside it: which seeds show it depends on the history stream,
   * so any version bump reshuffles the set.
   *
   * Split rather than dropped, and rather than tightening the segment filter. Tightening it would reject whole
   * segments for a metre of water and thin the street grid everywhere; splitting costs the wet span only. It is
   * the same treatment the wall gets, for the same reason.
   */
  private fun streetFeatures(
    nextId: () -> FeatureId,
    chain: Polyline,
    rank: Int,
    frame: TownFrame,
    world: WorldGround
  ): List<VectorFeature> {
    if (chain.length < params.streetSpacing * 2.0) return emptyList()

    // Sampled at the spacing the emitted street will itself be resampled at, so what is tested is what is
    // stamped. Testing more coarsely than the output is exactly how this was missed.
    val steps = max(2, (chain.length / params.streetSpacing).toInt())
    val points = (0..steps).map { chain.pointAt(chain.length * it / steps) }

    val out = ArrayList<VectorFeature>()
    var run = ArrayList<Vec2d>()

    fun flush() {
      if (run.size >= 2) {
        val line = runCatching { Polyline(run.toList()) }.getOrNull()
        if (line != null) streetFeature(nextId(), line, rank, frame)?.let { out.add(it) }
      }
      run = ArrayList()
    }

    for (point in points) {
      if (world.dry(point)) run.add(point) else flush()
    }
    flush()

    return out
  }

  /** A street: a road with a narrower cross-section and a higher stamp priority. */
  private fun streetFeature(
    featureId: FeatureId,
    chain: Polyline,
    rank: Int,
    frame: TownFrame
  ): PolylineFeature? {
    if (chain.length < params.streetSpacing * 2.0) return null

    val half = when (rank) {
      0 -> 3.2
      1 -> 2.4
      2 -> 1.7
      else -> 1.3
    }

    return runCatching {
      LinearFeatures.road(
        id = featureId,
        centerline = chain,
        stationSpacing = params.streetSpacing,
        kind = FeatureKind.STREET,
        surfaceElevation = { s -> frame.groundAt(chain.pointAt(s)) },
        halfWidth = { half },
        // A narrow shoulder, not the road's threefold default: a street's kerb is a kerb, and a six-metre
        // embankment either side would bulldoze the plots the street exists to serve.
        shoulder = { half * 0.5 },
        endTaper = half
      )
    }.getOrNull()
  }

  /**
   * A building: an oriented pad that levels its own ground, carrying what the materialiser needs.
   *
   * The pad is a terrace rather than a flat replace, with limits, so that a building on ground the town's
   * own grading could not level does not cut a ten-metre hole for itself. Where the limits bind, the
   * building's floor is above the ground at its back corners and the materialiser fills the difference as a
   * plinth - which is what a real building on a slope has.
   */
  private fun buildingFeature(featureId: FeatureId, building: Building, settlement: Int) =
    FootprintFeature(
      id = featureId,
      kind = FeatureKind.BUILDING,
      center = building.centre,
      bearing = building.bearing,
      halfLength = building.halfLength,
      halfWidth = building.halfWidth,
      profile = RadialProfiles.terrace(
        building.floorElevation, PAD_MAX_CUT, PAD_MAX_FILL
      ),
      attributes = StationTable.Builder(1)
        .channel(BuildingChannels.SETTLEMENT) { settlement.toDouble() }
        .channel(BuildingChannels.FUNCTION) { building.function.ordinal.toDouble() }
        .channel(BuildingChannels.STOREYS) { building.storeys.toDouble() }
        .channel(BuildingChannels.FLOOR_ELEVATION) { building.floorElevation }
        .channel(BuildingChannels.WALL_BLOCK) { building.wall.id.toDouble() }
        .channel(BuildingChannels.ROOF_BLOCK) { building.roof.id.toDouble() }
        .channel(BuildingChannels.ROOF_SHAPE) { building.roofShape.ordinal.toDouble() }
        .channel(BuildingChannels.DOOR_X) { building.doorBearing.x }
        .channel(BuildingChannels.DOOR_Y) { building.doorBearing.y }
        .channel(BuildingChannels.GRAMMAR_SEED) { building.grammarSeed.toDouble() }
        .build(),
      blend = BlendMode.REPLACE
    )

  // --- Walls ----------------------------------------------------------------------------------------

  /**
   * The wall circuit and its gates.
   *
   * The circuit encloses the extent the town had *when it was threatened*, from the population recorded at
   * that moment - so a town that kept growing has suburbs outside its own walls, which is what every walled
   * city that survived its wars ended up with and is visible from a long way off.
   *
   * Gates are the *gaps* between stretches rather than features that punch through one, and that is the
   * whole reason this is easy: nothing has to reconcile a wall with an opening at chunk time, because the
   * opening is a place where no wall was emitted.
   */
  private fun fortify(
    town: TownReader.Town,
    frame: TownFrame,
    graph: StreetGraph,
    world: WorldGround,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val enclosed = builtRadiusFor(max(town.wallPopulation, MIN_WALL_POPULATION), town.tier)
    val radius = min(enclosed * WALL_MARGIN, frame.builtRadius * WALL_MAX_SHARE)
    if (radius < params.gateWidth * 3.0) return emptyList()

    val ring = circle(frame.centre, radius, WALL_VERTICES)
    val ringLine = runCatching { Polyline(ring) }.getOrNull() ?: return emptyList()

    // Where the main streets leave: those are the gates. A gate that is not on a street is a gate nobody
    // uses, and a street with no gate is a street that stops at a wall.
    val crossings = ArrayList<Double>()
    for ((chain, rank) in graph.chains()) {
      if (rank > 1) continue
      for (hit in Intersections.of(chain, ringLine)) crossings.add(hit.sB)
    }

    // A ring the streets never reach still needs a way in, or the town is sealed.
    if (crossings.isEmpty()) {
      val bearing = frame.approaches.firstOrNull() ?: Vec2d(1.0, 0.0)
      val at = ringLine.project(frame.centre + bearing * radius)
      crossings.add(at.s)
    }
    crossings.sort()

    val out = ArrayList<VectorFeature>()
    val half = params.gateWidth * 0.5

    for (i in crossings.indices) {
      val from = crossings[i] + half
      val to = (if (i + 1 < crossings.size) crossings[i + 1] else crossings[0] + ringLine.length) - half
      if (to - from < params.gateWidth) continue

      out.addAll(wallStretches(nextId, ringLine, from, to, town, world))
    }

    for (s in crossings) {
      val at = ringLine.pointAt(s)
      val outward = (at - frame.centre).normalized()
      out.add(
        PointMarker(
          id = nextId(),
          kind = FeatureKind.GATE,
          position = at,
          attributes = StationTable.Builder(1)
            .channel(GateChannels.SETTLEMENT) { town.index.toDouble() }
            .channel(GateChannels.WIDTH) { params.gateWidth }
            .channel(GateChannels.BEARING_X) { outward.x }
            .channel(GateChannels.BEARING_Y) { outward.y }
            .build()
        )
      )
    }

    return out
  }

  /**
   * The wall between two gates, as one feature per stretch of it that is over dry land.
   *
   * **The ring is a circle and a coastal town's circle goes out to sea.** Nothing used to test for that, so a
   * walled port got a curtain wall running across open water - caught by the sweep's `nothing is built in water`
   * on 2 seeds in 30 at 256 cells, and latent long before this: whether it fires depends on the history stream,
   * so bumping any version that feeds `HistorySim` reshuffles which seeds show it. Confirmed by bumping the
   * history version alone at the previous commit, which reproduced the same two seeds and the same feature ids.
   *
   * Splitting into dry runs rather than dropping the whole stretch, because a stretch spans a gate-to-gate arc
   * and one wet station would otherwise delete half a town's defences. And a wall that stops at the waterfront
   * is not a compromise - it is what a fortified port *is*, since the sea is the defence on that side. Gates are
   * emitted separately, so a town whose wall is now shorter still has its gates and still satisfies
   * `checkWalledSettlementsHaveAGate`.
   */
  private fun wallStretches(
    nextId: () -> FeatureId,
    ring: Polyline,
    from: Double,
    to: Double,
    town: TownReader.Town,
    world: WorldGround
  ): List<MarkerFeature> {
    val steps = max(2, ((to - from) / WALL_STATION_SPACING).toInt())
    val points = (0..steps).map { ring.pointAt(from + (to - from) * it / steps) }

    val out = ArrayList<MarkerFeature>()
    var run = ArrayList<Vec2d>()

    fun flush() {
      // Two points minimum for a polyline, and a single dry station between two wet ones is a rock rather than
      // a wall - so a run of one is dropped rather than being turned into a degenerate feature.
      //
      // `runCatching` as well as the count, because `Polyline` requires two *distinct* points and two sampled
      // stations can coincide on a short arc. Dropping that guard - which the single-stretch version this
      // replaced did have - threw on seed 113 at 256 cells, 113 worlds into a 200-seed sweep.
      if (run.size >= 2) {
        val line = runCatching { Polyline(run.toList()) }.getOrNull()
        if (line != null) wallFeature(nextId(), line, town, world)?.let { out.add(it) }
      }
      run = ArrayList()
    }

    for (point in points) {
      if (world.dry(point)) run.add(point) else flush()
    }
    flush()

    return out
  }

  /** One contiguous run of wall, following the ground. */
  private fun wallFeature(
    featureId: FeatureId,
    line: Polyline,
    town: TownReader.Town,
    world: WorldGround
  ): MarkerFeature? {
    return MarkerFeature(
      id = featureId,
      kind = FeatureKind.TOWN_WALL,
      centerline = line,
      stations = StationTable.Builder(line.vertexCount)
        .channel(WallChannels.SETTLEMENT) { town.index.toDouble() }
        .channel(WallChannels.BASE_ELEVATION) {
          world.gradedGround(line.points[it], town.siteElevation)
        }
        .channel(WallChannels.HEIGHT) { params.wallHeight * (0.7 + 0.3 * town.wealth) }
        .channel(WallChannels.HALF_THICKNESS) { params.wallThickness * 0.5 }
        .channel(WallChannels.BLOCK) { BlockType.MASONRY.id.toDouble() }
        .build()
    )
  }

  private fun circle(centre: Vec2d, radius: Double, vertices: Int): List<Vec2d> {
    val out = ArrayList<Vec2d>(vertices + 1)
    for (i in 0..vertices) {
      val angle = i * 2.0 * PI / vertices
      out.add(Vec2d(centre.x + cos(angle) * radius, centre.y + sin(angle) * radius))
    }
    return out
  }

  companion object {
    val ID = StageId("towns")

    private const val SQUARE_METRES_PER_HECTARE = 10_000.0

    /** Largest share of the graded footprint the built area may take. Beyond it the ground is not level. */
    private const val FOOTPRINT_SHARE = 0.95

    /**
     * Floor on the built radius as a share of the footprint, for when reserving a plot would leave nothing.
     *
     * A hamlet's footprint is 90 m and a plot reaches 22 m into it, so the reservation is a quarter of the
     * whole disc. Without a floor a change to `lotDepth` could take a small settlement's street network to
     * zero and delete it silently; with one it comes out cramped, which is visible and is what a hamlet
     * should look like anyway.
     */
    private const val MIN_BUILT_SHARE = 0.5

    /** Cut and fill a building's own pad may add on top of the settlement's grading, in metres. */
    /**
     * What a building's own pad may cut and fill, in metres.
     *
     * Raised from 2.5 and 1.5 alongside the site check in [standsLevel], and the two belong together. The
     * check predicts the ground from the kilometre heightfield; the base the chunk actually generates adds
     * sub-kilometre detail noise on top, which on steep ground is worth a couple of metres and which nothing
     * at this tier can see. So the pad has to be able to swallow an error the size of that noise, or a site
     * the check passed still comes out with its floor standing clear of its ground.
     *
     * Four metres over an eighteen-metre footprint is a little over one in five - an ordinary amount of
     * digging for a house on a hillside, and far less than the settlement-wide grading's nine.
     */
    private const val PAD_MAX_CUT = 4.0
    private const val PAD_MAX_FILL = 3.0

    /**
     * How far the ground may still stand from the floor once the pad has done what it can, in metres.
     *
     * One voxel, which is the smallest difference that can exist at all - so this says the plinth may be a
     * single course of stone and no more. `TownStageTest.the ground under a building is level` asserts the
     * same number against the finished chunk columns, which is the check that matters: this predicts, that
     * one measures, and they have to agree or the prediction is worthless.
     */
    private const val PAD_MAX_RESIDUAL = 1.0

    /** The four corners of a footprint, as multiples of its half-extents. Matches `FootprintFeature.corners`. */
    private val CORNER_SIGNS = listOf(-1.0 to -1.0, 1.0 to -1.0, 1.0 to 1.0, -1.0 to 1.0)


    /** Metres of slack between the built extent at walling time and the circuit itself. */
    private const val WALL_MARGIN = 1.18

    /** Largest share of today's built radius the wall may sit at. Keeps suburbs outside, not inside. */
    private const val WALL_MAX_SHARE = 0.92

    /** Smallest population a circuit is sized for, so an early wall is not a garden fence. */
    private const val MIN_WALL_POPULATION = 500

    private const val WALL_VERTICES = 28
    private const val WALL_STATION_SPACING = 12.0
  }
}

/**
 * The settlements this stage lays out, joined from the placement marker and the history marker.
 *
 * Separate from the stage because the join is the fiddly part and worth reading on its own: two markers per
 * settlement, produced by two stages, matched on [SettlementChannels.INDEX] and
 * [HistoryChannels.INDEX], with a hard failure if either side has a gap.
 */
internal object TownReader {

  class Town(
    val index: Int,
    val position: Vec2d,
    val tier: SettlementTier,
    val cultureIndex: Int,
    val siteElevation: Double,
    val population: Int,
    val wealth: Double,
    val wallYear: Int,
    val wallPopulation: Int,
    val coastal: Boolean
  ) {
    val culture: Culture get() = Culture.byIndex(cultureIndex)
  }

  fun read(ctx: GenContext, region: CellRegion): List<Town> {
    val bounds = region.toWorld()
    val placed = ctx.features.query(bounds)
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(SettlementChannels.INDEX).toInt() }

    val history = ctx.features.query(bounds)
      .filter { it.kind == FeatureKind.SETTLEMENT_HISTORY }
      .filterIsInstance<PointMarker>()
      .associateBy { it.attribute(HistoryChannels.INDEX).toInt() }

    val distanceToOcean = ctx.layers.float(LayerId.DISTANCE_TO_OCEAN)

    return placed.keys.sorted().mapNotNull { index ->
      val site = placed.getValue(index)
      val past = history[index]
        ?: throw IllegalStateException("Settlement $index has no history marker; the stages disagree")

      // A site history never founded, or destroyed, gets no town. The `SETTLEMENT` marker means "somebody
      // would live here"; whether anybody does is the history marker's answer, and this is the one place
      // that distinction is acted on.
      if (past.attribute(HistoryChannels.FOUNDED_YEAR).toInt() == 0) return@mapNotNull null
      if (past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() != 0) return@mapNotNull null

      val population = past.attribute(HistoryChannels.POPULATION).toInt()
      if (population < MIN_POPULATION) return@mapNotNull null

      Town(
        index = index,
        position = site.position,
        tier = SettlementTier.entries[site.attribute(SettlementChannels.TIER).toInt()],
        cultureIndex = site.attribute(SettlementChannels.CULTURE).toInt(),
        siteElevation = site.attribute(SettlementChannels.ELEVATION),
        population = population,
        wealth = past.attribute(HistoryChannels.WEALTH),
        wallYear = past.attribute(HistoryChannels.WALL_YEAR).toInt(),
        wallPopulation = past.attribute(HistoryChannels.WALL_POPULATION).toInt(),
        coastal = distanceToOcean.sampleBilinear(site.position.x, site.position.y) < COASTAL_RANGE
      )
    }
  }

  /** Fewest people worth laying out streets for. Below this it is a farmstead, not a settlement. */
  private const val MIN_POPULATION = 12

  private const val COASTAL_RANGE = 6_000.0
}

/**
 * What the ground under a town is doing: how high, whether it may be built on, and which way things flow.
 *
 * ### The elevation this uses, and why it is not the coarse layer
 *
 * A building's floor has to be decided *here*, in a world-tier stage, while the height a chunk will actually
 * generate comes from `ChunkHeightSampler`, which is assembled after the pipeline and reads every feature
 * including the ones this stage is emitting. So this has to predict. The question is how much of the real
 * thing the prediction includes.
 *
 * It reads [WorldHeightField], the same base surface the chunk tier samples, rather than the coarse
 * [LayerId.ELEVATION] layer directly. The difference between the two is the sub-kilometre detail noise, and
 * this used to skip it on the argument that the building pad would absorb it. **The pad cannot absorb what
 * the site check cannot see.** [TownStage.standsLevel] rejects a lot whose pad would leave a residual over a
 * metre, and it made that judgement against a surface with no noise in it, so it predicted a residual of zero
 * for lots that finished two metres out and passed them - leaving buildings that far into the ground.
 *
 * Reading the real field costs an fbm evaluation per query and removes a whole class of disagreement between
 * what this stage thinks the ground is and what a player stands on. What it still does not include is the
 * features - rivers, roads, the town's own streets and grading - so `gradedGround` and `gradingFaded` model
 * the grading disc's arithmetic themselves, and that remains a second copy of one formula pinned by
 * `TownStageTest.the ground under a building is level`.
 *
 * A note on layering: `civ/` reaching into `geo/` for [WorldHeightField] is deliberate and narrow. It is not
 * another stage's derivation - it is the definition of the base surface, the thing `core/ChunkHeightSampler`
 * exists to sample, and the alternative is a third copy of the detail-noise formula in this file.
 */
internal class WorldGround(
  private val ctx: GenContext,
  private val region: CellRegion,
  private val params: TownParams
) {

  private val elevation: FloatLayer = ctx.layers.float(LayerId.ELEVATION)
  private val waterLevel: FloatLayer = ctx.layers.float(LayerId.WATER_LEVEL)
  private val flowDirection = ctx.layers.int(LayerId.FLOW_DIRECTION)

  /**
   * The base surface a chunk will sample, detail noise included.
   *
   * Built with `params.detail`, which `WorldParams` forwards from the same field `StandardWorld.assemble` gives
   * the chunk tier. The two have to agree: a disagreement shows up as buildings sitting slightly off the ground
   * everywhere, and it used to be guaranteed only by both sides defaulting.
   */
  private val base: BaseHeightField = WorldHeightField(
    elevation = elevation,
    hardness = ctx.layers.float(LayerId.ROCK_HARDNESS),
    seed = ctx.config.seed,
    seaLevel = ctx.config.seaLevel,
    params = params.detail
  )

  /** River channels, so a street is never routed into one. Cached per world, queried per settlement. */
  private val rivers: List<PolylineFeature> = ctx.features.query(region.toWorld())
    .filter { it.kind == FeatureKind.RIVER_CHANNEL }
    .filterIsInstance<PolylineFeature>()

  private val roads: List<PolylineFeature> = ctx.features.query(region.toWorld())
    .filter { it.kind == FeatureKind.ROAD }
    .filterIsInstance<PolylineFeature>()

  private val metres = region.resolution.metresPerCell

  /** The base surface, terraced the way the settlement's grading will terrace it. */
  fun gradedGround(at: Vec2d, siteElevation: Double): Double {
    val raw = base.heightAt(at.x, at.y)
    return when {
      raw > siteElevation -> max(siteElevation, raw - params.grading.maxCut)
      raw < siteElevation -> min(siteElevation, raw + params.grading.maxFill)
      else -> siteElevation
    }
  }

  /**
   * The same, but faded the way the grading disc actually fades.
   *
   * [gradedGround] answers "what would the grading do here if it were at full strength", which is the right
   * question for choosing a floor height and the wrong one for asking whether a pad can level its own ground.
   * `SettlementStage.gradingFor` builds the disc with `edgeFraction = 0.6`, so grading is at full strength
   * only inside the innermost 40% of the radius and tapers to nothing at the rim - and `builtRadius` puts a
   * great many buildings out in that taper. Predicting full grading out there overstates how level the ground
   * will be by metres, which is how a check against [gradedGround] passed buildings that the finished chunk
   * columns then showed standing four metres clear of their floors.
   *
   * The arithmetic mirrors `PointFeature.falloff` and `FeatureEvaluator.add`'s `REPLACE` case exactly. Two
   * copies of one formula is a poor thing, but the alternative is running the sampler on features that do not
   * exist yet, and the copy is at least pinned: `TownStageTest.the ground under a building is level` measures
   * the finished columns, so if these ever drift apart it fails.
   */
  fun gradingFaded(at: Vec2d, town: TownReader.Town): Double {
    val raw = base.heightAt(at.x, at.y)
    val radius = town.tier.footprintRadius
    val normalized = at.distanceTo(town.position) / radius
    if (normalized >= 1.0) return raw

    val fromEdge = 1.0 - normalized
    val weight = when {
      fromEdge >= GRADING_EDGE_FRACTION -> 1.0
      else -> PolylineFeature.smoothstep(fromEdge / GRADING_EDGE_FRACTION)
    }

    return raw + (gradedGround(at, town.siteElevation) - raw) * weight
  }

  /**
   * Whether this point is out of standing water. The water half of [buildable], on its own.
   *
   * Separate because a *wall* needs this test and not the rest of it: a curtain wall may run up a slope no
   * building could stand on, and it may cross a stream that no house would be built over. Reusing `buildable`
   * for a wall would shorten walls for reasons that have nothing to do with water.
   */
  fun dry(at: Vec2d): Boolean {
    val cellX = Math.floor(at.x / metres).toInt()
    val cellY = Math.floor(at.y / metres).toInt()
    return region.contains(cellX, cellY) && waterLevel[cellX, cellY].isNaN()
  }

  /** Whether anything may stand here: dry, gentle enough, inside the world, and clear of the channel. */
  fun buildable(at: Vec2d, town: TownReader.Town): Boolean {
    val cellX = Math.floor(at.x / metres).toInt()
    val cellY = Math.floor(at.y / metres).toInt()
    if (!region.contains(cellX, cellY)) return false
    if (!waterLevel[cellX, cellY].isNaN()) return false

    if (slopeAt(cellX, cellY) > params.maxBuildableSlope) return false

    for (river in rivers) {
      if (!river.bbox.contains(at.x, at.y)) continue
      val projection = river.centerline.project(at)
      val width = runCatching {
        river.stations.sample(river.stations.channel(Profiles.CHANNEL_WIDTH), projection.u)
      }.getOrDefault(0.0)
      if (projection.distance < width * 0.5 + params.riverClearance) return false
    }

    return true
  }

  private fun slopeAt(cellX: Int, cellY: Int): Double {
    val dx = (elevation[cellX + 1, cellY] - elevation[cellX - 1, cellY]) / (2.0 * metres)
    val dy = (elevation[cellX, cellY + 1] - elevation[cellX, cellY - 1]) / (2.0 * metres)
    return sqrt((dx * dx + dy * dy).toDouble())
  }

  /**
   * Unit directions from which roads arrive at a town.
   *
   * Taken a built radius either side of where the road passes closest, so a road running *through* a town
   * yields two approaches - which is what makes a through street rather than a dead end - and a road that
   * terminates yields one.
   */
  fun approachesTo(town: TownReader.Town, builtRadius: Double): List<Vec2d> {
    val out = ArrayList<Vec2d>()

    for (road in roads) {
      if (!road.bbox.expanded(builtRadius).contains(town.position.x, town.position.y)) continue
      val projection = road.centerline.project(town.position)
      if (projection.distance > builtRadius) continue

      for (side in intArrayOf(-1, 1)) {
        val reach = projection.s + side * builtRadius * APPROACH_REACH
        if (reach < 0.0 || reach > road.centerline.length) continue

        val direction = (road.centerline.pointAt(reach) - town.position).normalized()
        if (direction.lengthSquared < 0.5) continue
        // Two approaches within about twenty degrees leave as one wide street rather than two.
        if (out.none { it dot direction > APPROACH_DISTINCT }) out.add(direction)
      }
    }

    return out
  }

  /** Direction the prevailing wind blows towards. The noxious quarter goes this way. */
  fun downwindAt(at: Vec2d, config: WorldConfig): Vec2d =
    Winds.directionAt(ClimateStage.latitudeOf(at.y / config.heightMetres))

  /** Direction surface water leaves. Tanning and dyeing go this way. */
  fun downstreamAt(at: Vec2d): Vec2d {
    val cellX = Math.floor(at.x / metres).toInt()
    val cellY = Math.floor(at.y / metres).toInt()
    if (!region.contains(cellX, cellY)) return Vec2d(0.0, -1.0)

    val d = flowDirection[cellX, cellY]
    if (d == D8.NONE) return Vec2d(0.0, -1.0)
    return Vec2d(D8.DX[d].toDouble(), D8.DY[d].toDouble()).normalized()
  }

  private companion object {
    /** How far past the town, in built radii, an approach direction is measured. */
    const val APPROACH_REACH = 1.15

    /** Dot product above which two approach directions count as the same one. */
    const val APPROACH_DISTINCT = 0.94

    /** Must match `SettlementStage.gradingFor`'s `edgeFraction`. See [WorldGround.gradingFaded]. */
    const val GRADING_EDGE_FRACTION = 0.6
  }
}
