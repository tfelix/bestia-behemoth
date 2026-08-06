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
import net.bestia.worldgen.geo.GlacialStage
import net.bestia.worldgen.geo.WorldHeightField
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.AlluviumStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.hydro.PondStage
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.ConvexPolygons
import net.bestia.worldgen.vector.FeatureEvaluator
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
   * Step that a block-cut plot's dimensions are quantised to, in metres.
   *
   * Only the patched core uses it - a street-fronted plot already has exactly `lotFrontage` of frontage. A cut
   * block's leaves come out at whatever the recursion left, which includes slivers a metre across, and rounding
   * *down* to a step removes those and makes neighbouring blocks tile without a ragged join.
   *
   * Two and a half metres is a bay: the width of one window-and-a-bit, which is the unit a pre-industrial
   * building was actually laid out in. It is also the step a set of modular building meshes would want, so
   * quantising now costs nothing and keeps that door open.
   */
  val lotStep: Double = 2.5,

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
    // Zero would divide by it in `BlockSubdivider.quantiseDown`; anything above a plot would round every plot in a
    // patched core down to the floor of half a step and produce a town of identical sheds.
    require(lotStep > 0.0 && lotStep < lotFrontage) {
      "lotStep must be positive and under lotFrontage $lotFrontage, was $lotStep"
    }
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
    .put("lotStep", lotStep)
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

  // Back at 1 with every other stage - see Stage.version's "every stage is at 1, deliberately". This one had
  // briefly moved to 2 for a real code change, the town outline leaving its disc for a warped Ring, but that
  // was still pre-release: the promise a bump makes has no counterparty until a world outlives this repository,
  // which still has not happened. The git history holds the note that used to be here.
  override val version = 1

  override val paramsVersion get() = GenRng.hash(params.digest().value, Culture.catalogueDigest(), SettlementTier.catalogueDigest())
  /**
   * The glacial, alluvium and pond stages are here because a building stands on what they made.
   *
   * They are not read for their layers - they are read for their *features*, through
   * [WorldGround.groundFeatures]. A moraine, an alluvial fan and a delta all move the ground additively and
   * are never rasterised, so a town that cannot query them predicts a surface the chunk tier will not
   * generate, and its buildings come out buried. See the note on that field for how it was found.
   */
  override val dependencies = listOf(
    ClimateStage.ID, ErosionStage.ID, GlacialStage.ID, HydrologyStage.ID, AlluviumStage.ID, PondStage.ID,
    SettlementStage.ID, HistoryStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.STREET),
    StageOutput.Vector(FeatureKind.BUILDING),
    StageOutput.Vector(FeatureKind.DISTRICT),
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

    // One per town, because a FeatureEvaluator is not thread-safe and this runs on every core.
    val grading = world.around(town, builtRadius)

    val approaches = world.approachesTo(town, builtRadius)

    // What the town is strung out along, in the order a place actually grows: the water it crosses, then the
    // road it grew beside, then - for the settlement that has neither - a rolled bearing, because a town with no
    // reason to face one way still has no reason to be a circle.
    val axis = world.riverAxisAt(town.position, builtRadius)
      ?: approaches.firstOrNull()
      ?: (roll(0L, AXIS_SALT) * PI).let { Vec2d(cos(it), sin(it)) }

    val frame = TownFrame(
      centre = town.position,
      builtRadius = builtRadius,
      boundary = TownBoundary.of(
        centre = town.position,
        builtRadius = builtRadius,
        axis = axis,
        aspect = TownBoundary.aspectOf(roll, params.streets),
        seed = GenRng.hash(streamBase, town.index.toLong(), BOUNDARY_SALT),
        params = params.streets
      ),
      groundAt = { grading.groundAt(it) },
      buildable = { world.buildable(it) },
      approaches = approaches
    )

    // The population the layout is actually sized for; the same clamp `builtRadiusFor` applies, and the input to
    // how many patches the core wants.
    val housedBuildings = min(
      params.maxBuildingsPerSettlement,
      max(1, (town.population / params.peoplePerBuilding).toInt())
    )

    // A patched core, for the settlements big enough to have quarters. Below `TOWN` a settlement is one to three
    // patches across, so a partition of it is a partition of nothing - and the grown streets it already had are
    // what a village looks like anyway.
    val patches = if (town.tier <= SettlementTier.TOWN) {
      TownPatches.of(
        frame = frame,
        // The town's own outline, scaled down. Not a circle of the same area: the whole point of Phase 1 was that
        // the town is a shape, and a round core inside an elongated town would put it straight back.
        core = ConvexPolygons.scaledAbout(
          frame.boundary.vertices, frame.centre, TownPatches.CORE_SHARE
        ),
        wantedPatches = TownPatches.countFor((housedBuildings * TownPatches.CORE_SHARE).toInt()),
        channels = world.channelsNear(town.position, builtRadius),
        roll = roll
      )
    } else {
      emptyList()
    }

    val quarters = Quarters.assign(
      patches = patches,
      frame = frame,
      tier = town.tier,
      walled = town.wallYear != 0,
      downwind = world.downwindAt(town.position, config),
      downstream = world.downstreamAt(town.position),
      roll = roll
    )

    // Patch edges become streets, so the core's blocks are separated by real streets rather than by a gap. Fed to
    // the planner with the grown suburb streets so that both halves of the town are welded into *one* planar
    // graph - `LotPlanner`'s "does this plot reach across a street" test can only see what the graph holds, and
    // two graphs would let a suburb plot grow through a core street.
    val graph = StreetPlanner.plan(
      frame, town.culture.layout, roll, params.streets,
      extra = coreStreets(patches, quarters)
    )
    if (graph.edges.isEmpty()) return emptyList()

    // One field for the whole town, shared by both lot producers, so the core and the suburbs measure land value
    // against the same network rather than each normalising against its own.
    val distance = StreetDistance(graph, frame.centre)

    // The core first: it is the part a player walks through, and its plots are the ones worth keeping when the
    // suburbs would otherwise take the frontage.
    val coreLots = ArrayList<Lot>()
    for ((index, patch) in patches.withIndex()) {
      coreLots.addAll(
        BlockSubdivider.of(
          patch = patch,
          grain = Quarters.grainOf(quarters[index], town.culture.layout),
          frame = frame,
          // The **setback**, not the carriageway's half-width, when the setback is the larger. A street plot sits
          // `setback` metres back from its centreline; a block whose edge sat at the kerb instead would give the
          // core a wall of housefronts along the carriageway while the suburbs kept their verge, and the two halves
          // of one town would not agree about what a street looks like.
          streetWidthFor = { edge ->
            max(streetHalfWidth(rankOfEdge(patch, quarters, index, edge)) * KERB_TO_SETBACK, params.setback)
          },
          rankFor = { edge -> rankOfEdge(patch, quarters, index, edge) },
          distanceAt = { distance.at(it) },
          lotStep = params.lotStep,
          // A block plot may be up to half again the standard street plot in each direction, which is what a
          // patrician quarter's gardens and a temple's forecourt need. Beyond that the extra ground is yard.
          maxHalfFrontage = params.lotFrontage * MAX_BLOCK_PLOT,
          maxHalfDepth = params.lotDepth * MAX_BLOCK_PLOT,
          salt = index.toLong(),
          roll = roll
        )
      )
    }

    // A grown street that crosses a patch's middle is invisible to the block subdivision, which only knows the
    // streets on the patch's own edges. Dropped here, once the whole planar graph exists, rather than threaded into
    // the subdivider - the cut has no business knowing about the suburbs.
    coreLots.retainAll { !LotPlanner.blockedByStreet(it, graph) }

    val lots = coreLots + LotPlanner.subdivide(
      graph, frame, params.lotFrontage, params.lotDepth, params.setback,
      distance = distance, already = coreLots,
      // The core belongs to the blocks. Tested against the patches themselves rather than against the core outline,
      // so that ground a patch lost to a river or to a slope is still available to the suburbs.
      skip = { at -> patches.any { ConvexPolygons.contains(it.polygon, at) } }
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
    val limit = min(wanted, params.maxBuildingsPerSettlement)
    // The limit is handed to the zoning, not applied after it: the trade quotas are shares of the buildings the
    // town will have, and computing them from the plot count instead left a city with eighty houses in it.
    val functions = zoning.assign(lots, limit)

    // Walked in value order and *filled* to the limit rather than sliced at it, so that a lot rejected below
    // costs the town a worse lot rather than a building. Slicing first and filtering after would shrink every
    // town by however many bad sites its best lots happened to contain.
    val placed = ArrayList<Building>(limit)
    for (index in lots.indices.sortedWith(compareByDescending<Int> { zoning.valueOf(lots[it]) }.thenBy { it })) {
      if (placed.size >= limit) break

      // `index` is also the RNG salt for storeys, materials and roof, so it has to stay the lot's own index
      // and not a running count - renumbering here would change every building in the town.
      val building = zoning.buildingFor(lots[index], functions[index], index)
      if (standsLevel(building, grading)) placed.add(building)
    }

    val out = ArrayList<VectorFeature>(placed.size + graph.edges.size / 4 + 8)

    for ((rank, chain) in graph.chains().map { it.second to it.first }) {
      out.addAll(streetFeatures(nextId, chain, rank, frame, world))
    }

    for (building in placed) {
      out.add(buildingFeature(nextId(), building, town.index))
    }

    // Designed where the town has patches, inferred where it does not.
    //
    // A patch *is* a quarter - it was chosen as one, a street runs along its edge, and its blocks were cut to that
    // quarter's own grain - so its polygon is the district, exactly, and nothing has to be reconstructed from what
    // happened to get built. `Districts.of` keeps the villages and hamlets, where clustering the buildings is
    // still the only description available. See `Districts`' own KDoc for what the inferred version costs.
    if (patches.isEmpty()) {
      out.addAll(Districts.of(placed, town.index, params.lotFrontage, nextId))
    } else {
      out.addAll(
        Districts.ofPatches(patches, quarters, placed, town.index, town.nameSeed, town.cultureIndex, nextId)
      )
    }

    if (town.wallYear != 0) {
      out.addAll(fortify(town, frame, graph, world, grading, streamBase, nextId))
      out.addAll(citadelOf(patches, quarters, town, frame, world, grading, nextId))
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
  private fun standsLevel(building: Building, grading: WorldGround.Grading): Boolean {
    val floor = building.floorElevation
    val along = building.bearing * building.halfLength
    val across = building.bearing.perpendicular() * building.halfWidth

    for (corner in CORNER_SIGNS) {
      // The corner *and* a point two thirds of the way out to it. The corners alone are not a sufficient sample:
      // ground is not monotonic across a footprint, so a ridge or a hollow inside one shows at neither corner - and
      // `TownStageTest."the ground under a building is level"` measures exactly the two-thirds point, so predicting
      // only the corner was predicting somewhere the check does not look. It went unnoticed while every building was
      // a street plot ten metres deep; a block plot can be twenty-five, which is enough ground to hide relief in.
      for (share in CORNER_SAMPLES) {
        val at = building.centre + along * (corner.first * share) + across * (corner.second * share)
        val ground = grading.groundAt(at)
        val padded = when {
          ground > floor -> max(floor, ground - PAD_MAX_CUT)
          ground < floor -> min(floor, ground + PAD_MAX_FILL)
          else -> floor
        }
        if (abs(padded - floor) > PAD_MAX_RESIDUAL) return false
      }
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
    // Sized by the buildings the settlement will actually get, not by the people it has.
    //
    // `peoplePerHectare` is measured against the plot geometry, so it answers "how much ground do this many
    // people's buildings need" - and if the per-settlement cap is going to bind, the honest input is the
    // population those capped buildings represent. Feeding it the true population instead laid out streets for a
    // city three times the size of the one that would be built on them: the render of a nineteen-thousand-person
    // city was a small dense middle inside a shell of several hundred streets with nothing on them, because lots
    // are filled in descending land value and the outer ones never come up. The cap is a feature-count decision
    // and it is not this function's to argue with, but a town's *shape* should be the shape of what is there.
    val housed = min(population.toDouble(), params.maxBuildingsPerSettlement * params.peoplePerBuilding)
    val hectares = housed / params.peoplePerHectare
    // The radius of the *disc* this population wants. What the town needs is a shape of that area, whose bounding
    // circle is wider - hence the reach factor. Without it, de-circularising a town shrank it by the same factor,
    // which is a forty per cent cut in plots dressed up as a change of outline.
    val radius = sqrt(max(hectares, 0.05) * SQUARE_METRES_PER_HECTARE / PI) *
        params.streets.boundaryReachFactor

    // Floored above zero so a hamlet on a tight footprint still gets one ring of streets rather than none.
    val usable = (tier.footprintRadius * FOOTPRINT_SHARE - (params.setback + params.lotDepth))
      .coerceAtLeast(tier.footprintRadius * MIN_BUILT_SHARE)

    return min(radius, usable)
  }

  // --- The patched core ------------------------------------------------------------------------------

  /**
   * The edges of every patch, as streets.
   *
   * A shared edge is emitted twice, once from each side, and that is deliberate rather than tolerated:
   * `StreetPlanner.planarise` welds coincident endpoints and refuses a duplicate edge, so the second copy costs a
   * hash lookup and removes the need to decide which of the two patches owns their boundary. Trying to decide
   * would mean tracking edge identity through the Voronoi construction for no gain.
   */
  private fun coreStreets(patches: List<TownPatch>, quarters: List<DistrictKind>): List<StreetSegment> {
    val out = ArrayList<StreetSegment>()
    for ((index, patch) in patches.withIndex()) {
      val polygon = patch.polygon
      for (edge in polygon.indices) {
        out.add(
          StreetSegment(
            polygon[edge],
            polygon[(edge + 1) % polygon.size],
            rankOfEdge(patch, quarters, index, edge)
          )
        )
      }
    }
    return out
  }

  /**
   * How important the street along one edge of a patch is.
   *
   * The rank decides the carriageway width, how far the block is set back from it, and - through
   * `Zoning.valueOf` - what gets built facing it. So this is where a town's high street is actually decided, and
   * the rule is that **an artery is a street the important quarters are on**: the market, the ways in, and the
   * citadel. That produces a network of main streets radiating from the market to the gates without routing
   * anything, because the quarters were already placed at the market and at the gates.
   */
  private fun rankOfEdge(
    patch: TownPatch,
    quarters: List<DistrictKind>,
    index: Int,
    edge: Int
  ): Int {
    val neighbour = patch.edgeNeighbour.getOrElse(edge) { -1 }

    // The outline of the core: the road that runs round the outside of the built-up middle, and what the suburbs
    // hang off. Never an artery - a ring road that outranked the high street would put the shops on the edge.
    if (neighbour < 0) return CORE_OUTLINE_RANK

    val here = quarters.getOrNull(index)
    val there = quarters.getOrNull(neighbour)
    val arterial = here in ARTERIAL_QUARTERS || there in ARTERIAL_QUARTERS
    return if (arterial) 0 else 1
  }

  /**
   * Half the carriageway width for a street of this rank, in metres.
   *
   * Extracted from [streetFeature] because the block subdivider needs the same number: a block is set back from
   * each of its edges by the width of the street on that edge, and if the two disagreed then either the blocks
   * would overlap the carriageway or a gap would open along every artery in the town.
   */
  private fun streetHalfWidth(rank: Int): Double = when (rank) {
    0 -> 3.2
    1 -> 2.4
    2 -> 1.7
    else -> 1.3
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

    val half = streetHalfWidth(rank)

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
    grading: WorldGround.Grading,
    streamBase: Long,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val enclosed = builtRadiusFor(max(town.wallPopulation, MIN_WALL_POPULATION), town.tier)
    val radius = min(enclosed * WALL_MARGIN, frame.builtRadius * WALL_MAX_SHARE)
    if (radius < params.gateWidth * 3.0) return emptyList()

    val out = ArrayList<VectorFeature>()

    out.addAll(
      circuitOf(
        town, frame, graph, world, grading, nextId,
        radius = radius,
        seed = GenRng.hash(streamBase, town.index.toLong(), WALL_WARP_SALT),
        inner = false
      )
    )

    /*
     * A second circuit around the old core, for a city that outgrew the first one.
     *
     * History records both `wallYear` and `wallPopulation`, so the size the town was when it was first threatened
     * is known independently of the size it is now - and a place that has since multiplied several times over did
     * not knock its old wall down, it built a bigger one outside and kept the first as the boundary of the old
     * town. That inner circuit is one of the most legible things about a real city from above, and it costs
     * nothing here but a second call: the enclosed radius for the *original* population is already what
     * `builtRadiusFor` computes.
     */
    val original = builtRadiusFor(max(town.wallPopulation, MIN_WALL_POPULATION), town.tier)
    if (radius > original * INNER_CIRCUIT_GROWTH && original > params.gateWidth * 3.0) {
      out.addAll(
        circuitOf(
          town, frame, graph, world, grading, nextId,
          radius = original * WALL_MARGIN,
          seed = GenRng.hash(streamBase, town.index.toLong(), INNER_WALL_SALT),
          inner = true
        )
      )
    }

    return out
  }

  /**
   * One closed circuit: its wall stretches, its gates, its towers and its gatehouses.
   *
   * Factored out of [fortify] so that the outer wall and an inner one are the same code rather than the same code
   * twice. `inner` reaches only two things - the channel that tells them apart, and whether towers are built,
   * since an inner circuit that a city has grown past is a boundary rather than a defence and its towers would
   * have been taken down for the stone long before a player saw it.
   */
  private fun circuitOf(
    town: TownReader.Town,
    frame: TownFrame,
    graph: StreetGraph,
    world: WorldGround,
    grading: WorldGround.Grading,
    nextId: () -> FeatureId,
    radius: Double,
    seed: Long,
    inner: Boolean
  ): List<VectorFeature> {
    val ring = circuit(frame, radius, seed)
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

    val gates = spacedGates(crossings, ringLine.length)

    val out = ArrayList<VectorFeature>()
    val half = params.gateWidth * 0.5

    for (i in gates.indices) {
      val from = gates[i] + half
      val to = (if (i + 1 < gates.size) gates[i + 1] else gates[0] + ringLine.length) - half
      if (to - from < params.gateWidth) continue

      out.addAll(wallStretches(nextId, ringLine, from, to, town, world, grading, inner))
      // A tower where a stretch is long enough to want one, which is what makes a circuit read as fortified rather
      // than as a fence. Skipped on an inner circuit; see this function's own note.
      if (!inner) out.addAll(towersAlong(nextId, ringLine, from, to, town, frame, world, grading))
    }

    for (s in gates) {
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
      // The gatehouse: the wall thickened either side of the opening, which is what a gate actually was. Emitted as
      // its own short stretch rather than as a channel on the arc, because the stretch either side of a gate is
      // already a separate feature - so this needs no new geometry and no new channel.
      out.addAll(gatehouseAt(nextId, ringLine, s, town, world, grading, inner))
    }

    return out
  }

  /**
   * The citadel: a wall around its own patch, and a keep standing in it.
   *
   * `Quarters` already chose which patch it is - the compact one on the core's outline, furthest from the market -
   * because that is a question about location and belongs with the other quarters. What is left for here is the
   * fortification, and it is the same two pieces a town has: a circuit, and something inside it.
   *
   * The wall follows the patch's own edges rather than a shape of its own, so the citadel sits inside the street
   * network instead of across it: the patch's boundary is already a set of streets, and a wall laid along them
   * encloses exactly the block those streets bound. The keep is the patch inset by two main streets' width, which
   * leaves a bailey between the keep and the wall - and a bailey is most of what a castle is.
   *
   * No gate marker. A castle's gate opens onto the town rather than onto the country, so it is not a way *into* the
   * settlement and `NavGraphStage` would wrongly join it to the road network. The opening is still there - it is the
   * gap `wallStretches` leaves where the wall is cut for the entrance arc.
   */
  private fun citadelOf(
    patches: List<TownPatch>,
    quarters: List<DistrictKind>,
    town: TownReader.Town,
    frame: TownFrame,
    world: WorldGround,
    grading: WorldGround.Grading,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val index = quarters.indexOf(DistrictKind.CITADEL)
    if (index < 0) return emptyList()

    val patch = patches[index]
    val out = ArrayList<VectorFeature>()

    // Closed, so the last vertex repeats the first the way `circuit` does.
    val closed = patch.polygon + patch.polygon.first()
    val wall = runCatching { Polyline(closed) }.getOrNull() ?: return emptyList()

    // One opening, on the side facing the town. Everything else is wall.
    val entrance = wall.project(frameCentreOf(wall)).s
    val half = params.gateWidth * 0.5
    out.addAll(
      wallStretches(
        nextId, wall, entrance + half, entrance + wall.length - half, town, world, grading,
        inner = false, thickness = params.wallThickness * CITADEL_THICKNESS
      )
    )

    val keepPlan = ConvexPolygons.inset(patch.polygon) { streetHalfWidth(0) * KEEP_INSET }
    val keep = ConvexPolygons.orientedExtent(keepPlan)
    if (keep != null && frame.encloses(keep.centre)) {
      val floor = grading.groundAt(keep.centre)
      if (floor.isFinite()) {
        out.add(
          buildingFeature(
            nextId(),
            Building(
              centre = keep.centre,
              bearing = keep.along,
              halfLength = min(keep.halfAlong, params.lotDepth * KEEP_MAX_HALF),
              halfWidth = min(keep.halfAcross, params.lotDepth * KEEP_MAX_HALF),
              function = BuildingFunction.FORTIFICATION,
              storeys = KEEP_STOREYS,
              wall = BlockType.MASONRY,
              roof = BlockType.MASONRY,
              roofShape = RoofShape.FLAT,
              floorElevation = floor,
              grammarSeed = 0L,
              doorBearing = (frameCentreOf(wall) - keep.centre).normalized()
            ),
            town.index
          )
        )
      }
    }

    return out
  }

  /**
   * Gates thinned so that no two are within [MIN_GATE_SPACING] of each other along the circuit.
   *
   * Every rank-0 and rank-1 street crossing the ring used to become a gate, and with a patched core those crossings
   * arrive in clusters: the arteries radiate from the market to the gate quarters, and several of them cross the
   * circuit within a few metres of each other. Each crossing then punched its own opening, and the stretch left
   * between two of them was shorter than a gate and dropped - so a stretch of curtain wall turned into a row of
   * gateposts. Thinning to the *first* of each cluster keeps the gate on a real street and gives the wall back the
   * span between them.
   */
  private fun spacedGates(crossings: List<Double>, circumference: Double): List<Double> {
    if (crossings.isEmpty()) return crossings

    val spacing = max(MIN_GATE_SPACING, params.gateWidth * 3.0)
    val out = ArrayList<Double>(crossings.size)
    for (s in crossings) {
      if (out.isEmpty() || s - out.last() >= spacing) out.add(s)
    }

    // The circuit is closed, so the last gate has to clear the first one the long way round as well. Dropping the
    // last rather than the first keeps the choice deterministic and independent of where arc length happens to
    // start.
    if (out.size > 1 && (circumference - out.last() + out.first()) < spacing) out.removeAt(out.size - 1)

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
    world: WorldGround,
    grading: WorldGround.Grading,
    inner: Boolean = false,
    thickness: Double = params.wallThickness
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
        if (line != null) wallFeature(nextId(), line, town, grading, inner, thickness)?.let { out.add(it) }
      }
      run = ArrayList()
    }

    for (point in points) {
      if (world.dry(point)) run.add(point) else flush()
    }
    flush()

    return out
  }

  /**
   * The gatehouse: a short run of doubled-thickness wall either side of a gate.
   *
   * What a medieval gate actually was - not a hole in a curtain wall but a building with the road running through
   * it. Expressed as a thicker *stretch* rather than as a new feature kind or a new channel, which is what makes it
   * nearly free: the materialiser already lays a wall from `HALF_THICKNESS`, so a stretch with twice the value
   * comes out as a mass of masonry flanking the opening with no chunk-tier change at all.
   */
  private fun gatehouseAt(
    nextId: () -> FeatureId,
    ring: Polyline,
    at: Double,
    town: TownReader.Town,
    world: WorldGround,
    grading: WorldGround.Grading,
    inner: Boolean
  ): List<MarkerFeature> {
    val half = params.gateWidth * 0.5
    val reach = params.gateWidth * GATEHOUSE_REACH
    val out = ArrayList<MarkerFeature>()

    // One flank each side. Wrapped arc lengths are handled by `Polyline.pointAt`'s own clamping at the ends, so a
    // gate near the seam gets a shorter gatehouse rather than a wrong one.
    for (side in intArrayOf(-1, 1)) {
      val from = if (side < 0) at - half - reach else at + half
      val to = if (side < 0) at - half else at + half + reach
      if (from < 0.0 || to > ring.length) continue
      out.addAll(
        wallStretches(
          nextId, ring, from, to, town, world, grading, inner,
          thickness = params.wallThickness * GATEHOUSE_THICKNESS
        )
      )
    }

    return out
  }

  /**
   * Towers along one gate-to-gate arc.
   *
   * `BuildingFunction.FORTIFICATION` has existed since the wall did and **nothing has ever emitted one** - it is
   * declared, it is masonry-walled, `TownStructures` will materialise it like any other footprint, and no placer
   * ever asked for it. This is its first user, which is also why it needs no voxel work: a tower is an ordinary
   * `FootprintFeature`, so the existing building path already builds a square masonry block with a roof on it.
   *
   * Placed at a spacing rather than at every vertex of the circuit. A vertex of a warped ring is wherever the fbm
   * put it, which is not where a mason would put a tower; a spacing along the arc is, and it also means the towers
   * of a small circuit do not crowd into each other.
   */
  private fun towersAlong(
    nextId: () -> FeatureId,
    ring: Polyline,
    from: Double,
    to: Double,
    town: TownReader.Town,
    frame: TownFrame,
    world: WorldGround,
    grading: WorldGround.Grading
  ): List<VectorFeature> {
    val span = to - from
    if (span < TOWER_SPACING) return emptyList()

    val count = (span / TOWER_SPACING).toInt()
    val out = ArrayList<VectorFeature>(count)

    for (i in 1..count) {
      // Interior points of the arc, so a tower never lands on the gate at either end of it.
      val s = from + span * i / (count + 1)
      val at = ring.pointAt(s)
      if (!world.dry(at)) continue
      // Inside the town, like every other building. A tower is emitted as a `BUILDING`, so it is subject to
      // `Invariants.checkBuildingsBelongToTheirSettlement` exactly as a house is.
      if (!frame.encloses(at)) continue

      val along = ring.tangentAt(s)
      if (along.lengthSquared < 0.5) continue

      val half = params.wallThickness * TOWER_SIZE
      val floor = grading.groundAt(at)
      if (!floor.isFinite()) continue

      out.add(
        buildingFeature(
          nextId(),
          Building(
            centre = at,
            bearing = along,
            halfLength = half,
            halfWidth = half,
            function = BuildingFunction.FORTIFICATION,
            // Taller than the curtain it stands on, which is the whole point of a tower.
            storeys = TOWER_STOREYS,
            wall = BlockType.MASONRY,
            roof = BlockType.MASONRY,
            roofShape = RoofShape.FLAT,
            floorElevation = floor,
            grammarSeed = 0L,
            // Inward, so the door is on the town's side of its own wall.
            doorBearing = (frameCentreOf(ring) - at).normalized()
          ),
          town.index
        )
      )
    }

    return out
  }

  /** Centre of a closed circuit, as the mean of its vertices. Only used to face a tower's door inwards. */
  private fun frameCentreOf(ring: Polyline): Vec2d {
    val points = ring.points
    var x = 0.0
    var y = 0.0
    for (p in points) {
      x += p.x
      y += p.y
    }
    return Vec2d(x / points.size, y / points.size)
  }

  /** One contiguous run of wall, following the ground. */
  private fun wallFeature(
    featureId: FeatureId,
    line: Polyline,
    town: TownReader.Town,
    grading: WorldGround.Grading,
    inner: Boolean,
    thickness: Double
  ): MarkerFeature? {
    return MarkerFeature(
      id = featureId,
      kind = FeatureKind.TOWN_WALL,
      centerline = line,
      stations = StationTable.Builder(line.vertexCount)
        .channel(WallChannels.SETTLEMENT) { town.index.toDouble() }
        .channel(WallChannels.BASE_ELEVATION) {
          grading.groundAt(line.points[it])
        }
        // An inner circuit is older and was built for a smaller town, so it is lower. Not a separate parameter:
        // the same wealth term the outer wall uses, scaled by the fact that it is the old wall.
        .channel(WallChannels.HEIGHT) {
          params.wallHeight * (0.7 + 0.3 * town.wealth) * (if (inner) INNER_WALL_HEIGHT else 1.0)
        }
        .channel(WallChannels.HALF_THICKNESS) { thickness * 0.5 }
        .channel(WallChannels.CIRCUIT) { if (inner) 1.0 else 0.0 }
        .channel(WallChannels.BLOCK) { BlockType.MASONRY.id.toDouble() }
        .build()
    )
  }

  /**
   * The closed curve a wall is laid along: the town's own outline, shrunk to the walled extent.
   *
   * This was `circle(centre, radius, 28)` - a perfect 28-gon with no wander in it at all, which made the wall
   * the single most obviously surveyed thing in any settlement. The plots and streets around it had at least
   * grown; the wall was drawn with a compass.
   *
   * Built by scaling [TownFrame.boundary] about the town centre rather than by warping a fresh circle, and that
   * is the point: a circuit has to *agree* with the town it encloses. A wall warped independently would bulge
   * where the town does not and cut across it where it does, which reads worse than a circle because it looks
   * like two different towns. Scaling a star-shaped ring about its own centre keeps it simple - the vertices
   * hold their angular order and only their radii change - and the same argument `Districts.ringAround` makes
   * for pushing a hull outwards applies in reverse here.
   *
   * The seed is taken and folded into the vertex jitter rather than being used to draw the shape, so that two
   * towns of the same size on the same outline still get visibly different circuits.
   *
   * Returned as a closed vertex list - first point repeated - because a [Polyline] is what the stretch splitting
   * and the gate arithmetic below both want, and a `Ring` would have to be reopened immediately.
   */
  private fun circuit(frame: TownFrame, radius: Double, seed: Long): List<Vec2d> {
    val boundary = frame.boundary.vertices
    val reach = boundary.maxOf { it.distanceTo(frame.centre) }
    val scale = radius / reach

    val out = ArrayList<Vec2d>(boundary.size + 1)
    for ((i, vertex) in boundary.withIndex()) {
      // A little per-vertex give, so a circuit is not a scale model of the built edge. Small enough that the
      // wall still recognisably encloses the town, which is the property the whole approach rests on.
      val jitter = 1.0 + (GenRng.hashUnit(seed, i.toLong()) - 0.5) * 2.0 * WALL_VERTEX_JITTER
      out.add(frame.centre + (vertex - frame.centre) * (scale * jitter))
    }
    out.add(out.first())
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

    /**
     * How far out along each corner diagonal the ground is sampled, as shares of the half-extents.
     *
     * The corner, and the two-thirds point `TownStageTest` measures at. Two samples per corner rather than a grid,
     * because the pad is a terrace about the floor and what matters is the extremes of the ground it has to
     * swallow - which on a footprint this size are at the edges and along the diagonals.
     */
    private val CORNER_SAMPLES = doubleArrayOf(1.0, 0.66)

    /**
     * Carriageway half-width to kerb-to-plot distance, as a multiple.
     *
     * `LinearFeatures.road` gives a street a shoulder of half its half-width on each side, so the ground a street
     * actually disturbs reaches one and a half times its half-width. A block set back by less than that sits on
     * its own street's embankment.
     */
    private const val KERB_TO_SETBACK = 1.5


    /** Metres of slack between the built extent at walling time and the circuit itself. */
    private const val WALL_MARGIN = 1.18

    /** Largest share of today's built radius the wall may sit at. Keeps suburbs outside, not inside. */
    private const val WALL_MAX_SHARE = 0.92

    /** Smallest population a circuit is sized for, so an early wall is not a garden fence. */
    private const val MIN_WALL_POPULATION = 500

    /**
     * How much each circuit vertex strays from the scaled built edge, as a fraction of its radius.
     *
     * Small deliberately. The circuit's job is to look like the wall of *this* town, so it has to track the
     * outline it was taken from; the jitter is there to stop it being a scale model of it, not to give the wall
     * an outline of its own.
     */
    private const val WALL_VERTEX_JITTER = 0.06

    private const val WALL_STATION_SPACING = 12.0

    /**
     * Fewest metres of circuit between two gates.
     *
     * Without it, every artery crossing the ring punched its own opening - and with a patched core the arteries
     * radiate from the market to the gate quarters and arrive at the circuit in clusters, so a stretch of curtain
     * wall became a row of gateposts. Forty metres is several times a gate's own width and is about the shortest
     * length of wall that reads as wall.
     */
    private const val MIN_GATE_SPACING = 40.0

    /** Metres of circuit between towers. A bowshot apart, which is what decided it historically. */
    private const val TOWER_SPACING = 55.0

    /** A tower's half-extent, as a multiple of the wall's thickness. */
    private const val TOWER_SIZE = 1.6

    /** Storeys of a wall tower. Two above the curtain, which is what makes it visible from outside the town. */
    private const val TOWER_STOREYS = 3

    /** How far a gatehouse's thickened wall reaches either side of the opening, as multiples of the gate's width. */
    private const val GATEHOUSE_REACH = 0.9

    /** How much thicker a gatehouse is than the curtain it interrupts. */
    private const val GATEHOUSE_THICKNESS = 2.2

    /**
     * How much bigger than its original circuit a town must be before it gets a second, inner one.
     *
     * A wall is only worth keeping as a boundary if the town plainly grew past it. Below this the two circuits
     * would sit within a few metres of each other and read as one thick wall.
     */
    private const val INNER_CIRCUIT_GROWTH = 1.45

    /** An inner circuit's height, as a share of the outer one's. Older, lower, and built for a smaller town. */
    private const val INNER_WALL_HEIGHT = 0.8

    /** How much thicker a citadel's wall is than the town's. A castle was built to outlast the town around it. */
    private const val CITADEL_THICKNESS = 1.4

    /** Multiples of an arterial street's half-width that the keep is set back inside the citadel's wall. */
    private const val KEEP_INSET = 4.0

    /** Cap on a keep's half-extent, as a multiple of a plot's depth. Above it a keep is a curtain wall with a roof. */
    private const val KEEP_MAX_HALF = 1.4

    /** Storeys of a keep. The tallest thing in any settlement, which is what a keep was for. */
    private const val KEEP_STOREYS = 4

    /**
     * Rank of the street around the outside of a patched core.
     *
     * Two rather than one, so the ring around the middle of a town does not outrank the streets crossing it. A
     * high street is a street that goes *through* a place; a ring road that outranked it would put the shops and
     * the inns on the edge of the core facing outwards, which is the wrong way round.
     */
    private const val CORE_OUTLINE_RANK = 2

    /**
     * Largest block plot, as a multiple of the standard plot's *full* frontage and depth taken as half-extents.
     *
     * 0.75 makes the cap one and a half standard plots each way - a plot of 18.75 by 27 metres against a street
     * plot's 12.5 by 18. Large enough for a guildhall's forecourt and a patrician's garden; small enough that no
     * cottage comes out sixty metres long, which is what an uncapped park leaf produced.
     */
    private const val MAX_BLOCK_PLOT = 0.75

    /**
     * The quarters whose streets are arteries.
     *
     * The market because everything converges on it, the gates because that is what a road arriving becomes, and
     * the citadel because a garrison needs to reach the walls. Naming the *quarters* rather than routing paths is
     * what makes the main streets fall out of the layout: the market is already central and the gate quarters are
     * already at the edge facing the roads, so the arteries between them already run the right way.
     */
    private val ARTERIAL_QUARTERS = setOf(DistrictKind.MARKET, DistrictKind.GATE, DistrictKind.CITADEL)

    /** Salts for the per-town rolls this stage makes itself, rather than through a planner. */
    private const val AXIS_SALT = 0x51L
    private const val BOUNDARY_SALT = 0x52L
    private const val WALL_WARP_SALT = 0x53L
    private const val INNER_WALL_SALT = 0x54L
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
    val coastal: Boolean,
    /**
     * The seed the settlement's own name comes from. Read here so its *quarters* can be named from it too.
     *
     * A `Double` channel holding a 64-bit seed loses its low bits, which the history stage accepts because a name
     * seed only has to be an arbitrary stable number. The same is true of a quarter's, so passing it on through the
     * same lossy hop costs nothing that was not already given up.
     */
    val nameSeed: Long
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
        coastal = distanceToOcean.sampleBilinear(site.position.x, site.position.y) < COASTAL_RANGE,
        nameSeed = past.attribute(HistoryChannels.NAME_SEED).toLong()
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
 * features it has not emitted yet - its own streets, and the roads that will be cut through it. The
 * settlement grading it *does* now read directly, through [WorldGround.Grading], rather than modelling the
 * disc's arithmetic in two hand-written approximations as it used to.
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

  /**
   * **Every** feature that moves the ground, not only the settlement grading discs.
   *
   * The kind filter that used to be here said `SETTLEMENT_GRADING`, and that was the bug. What a building's
   * pad has to level is the height a *chunk* will generate, and a chunk applies every feature over the
   * column - so predicting from the raster plus one kind is predicting from a surface that does not exist.
   *
   * The one that found it is `MORAINE`, with an `ADD` blend. `GlacialStage.carveInto` rasterises only the
   * `MIN` carves, precisely so that the moraine ridge stays a vector-tier thing - which means the `ELEVATION`
   * layer this reads its base from does *not* contain it, while the chunk does. A town on a moraine therefore
   * had ten metres of ridge under it that nothing at this tier could see, `standsLevel` passed the lot, and
   * the building came out with its floor buried. The same hole was open for `ALLUVIAL_FAN` and `DELTA`, which
   * are additive for the same reason, and for the glacial troughs and vector ponds in the other direction.
   *
   * It stayed hidden because it needs a settlement to fall on a landform of a kind that is rare where people
   * build. The version reset that reshuffled every RNG stream is what put one there - which is the argument
   * for doing a reset at all, rather than the reset having caused anything.
   */
  private val groundFeatures: List<VectorFeature> = ctx.features.query(region.toWorld())
    .filter { it.affectsHeight }

  /**
   * The ground around one settlement as a chunk will find it, evaluated by the real feature evaluator.
   *
   * Per town rather than per world for two reasons. [FeatureEvaluator] carries scratch state and is
   * documented as not thread-safe, and towns are laid out in parallel - so one shared instance would be a
   * data race in the most expensive stage in the pipeline. And narrowing to the features that actually reach
   * this town turns a loop over every landform in the world, run once per building corner, into a loop over
   * a handful.
   */
  fun around(town: TownReader.Town, reach: Double): Grading {
    val margin = reach + town.tier.footprintRadius
    val box = Aabb(
      town.position.x - margin, town.position.y - margin,
      town.position.x + margin, town.position.y + margin
    )
    return Grading(base, groundFeatures.filter { it.bbox.intersects(box) })
  }

  /**
   * The base surface with every height feature stamped on it, exactly as a chunk will see it.
   *
   * Replaces two hand-written approximations, and the replacement is not only shorter - it is *correct in a
   * place they were not*. The full-strength version answered "what would grading do here if it were at full
   * strength", which is right at the centre and wrong at the rim, where the disc has faded to nothing and a
   * great many buildings stand; predicting full grading out there overstated how level the ground would be
   * by metres. The faded version fixed that for building corners only, leaving streets and wall footings
   * still reading the full-strength answer. Going through the feature fixes all three at once, and there is
   * no longer a copy of `edgeFraction` to keep in step with the one in `SettlementStage`.
   *
   * The features this stage produces itself - streets, buildings, walls - are deliberately absent, because
   * they are not in the store yet. That is the right answer: what a lot is judged against is the ground
   * before anything is built on it.
   */
  internal class Grading(private val base: BaseHeightField, features: List<VectorFeature>) {

    private val evaluator = FeatureEvaluator(features)

    fun groundAt(at: Vec2d): Double = evaluator.heightAt(at.x, at.y, base.heightAt(at.x, at.y))
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
  fun buildable(at: Vec2d): Boolean {
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

  /**
   * River centrelines passing near this town, so a patch can be cut at the bank rather than across the water.
   *
   * The channel has always been a *veto* here - [buildable] refuses a plot inside it - and a veto is enough for a
   * plot and not enough for a block: a patch that straddles the water is one quarter on paper and two on the
   * ground. See `TownPatches.cutAtChannels`.
   */
  fun channelsNear(at: Vec2d, reach: Double): List<Polyline> = rivers
    .filter { it.bbox.expanded(reach).contains(at.x, at.y) && it.centerline.project(at).distance <= reach }
    .map { it.centerline }

  /**
   * Which way the river through this town runs, or null if no channel comes near enough to shape it.
   *
   * The town's long axis, when it has one. A settlement on a river is *on* the river - it faces the water,
   * spreads along both banks and crosses at one place - and until now the channel only ever subtracted from a
   * town through [buildable], so a river city came out as a disc with a bite in it.
   *
   * The largest channel within reach rather than the nearest, because a town sited on a tributary a hundred
   * metres from its confluence with a great river belongs to the great river. Width is the station channel every
   * river carries, and it is what `buildable` already reads to keep plots out of the water.
   */
  fun riverAxisAt(at: Vec2d, reach: Double): Vec2d? {
    var best: Vec2d? = null
    var widest = 0.0

    for (river in rivers) {
      if (!river.bbox.expanded(reach).contains(at.x, at.y)) continue
      val projection = river.centerline.project(at)
      if (projection.distance > reach) continue

      val width = runCatching {
        river.stations.sample(river.stations.channel(Profiles.CHANNEL_WIDTH), projection.u)
      }.getOrDefault(0.0)
      if (width <= widest) continue

      val tangent = river.centerline.tangentAt(projection.s)
      if (tangent.lengthSquared < 0.5) continue

      widest = width
      best = tangent
    }

    return best
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

  }
}
