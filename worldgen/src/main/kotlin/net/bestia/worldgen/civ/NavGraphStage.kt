package net.bestia.worldgen.civ

import net.bestia.worldgen.core.CellRegion
import net.bestia.worldgen.core.GenContext
import net.bestia.worldgen.core.LayerId
import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdge
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.NavNode
import net.bestia.worldgen.core.NavNodeId
import net.bestia.worldgen.core.NavNodeKind
import net.bestia.worldgen.core.Parallel
import net.bestia.worldgen.core.Params
import net.bestia.worldgen.core.ParamsDigest
import net.bestia.worldgen.core.ParamsText
import net.bestia.worldgen.core.Resolution
import net.bestia.worldgen.core.Stage
import net.bestia.worldgen.core.StageId
import net.bestia.worldgen.core.StageOutput
import net.bestia.worldgen.core.StageResult
import net.bestia.worldgen.core.StageScale
import net.bestia.worldgen.core.Timings
import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.history.HistoryChannels
import net.bestia.worldgen.history.HistoryStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.karst.CaveStage
import net.bestia.worldgen.mana.CorruptionStage
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.MarkerFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Vec2d
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.tan

/** Tuning for [NavGraphStage]. */
data class NavParams(

  /**
   * Spacing of the open-country node lattice, in metres.
   *
   * The main lever on graph size, and quadratic in it: halving this quadruples the wilderness nodes. Eight
   * kilometres is about an hour's walk, which is the granularity a long journey actually needs - the fine
   * detail of getting round a particular boulder is the chunk tier's problem, not this one's.
   */
  val wildernessSpacingMetres: Double = 8_000.0,

  /** As a fraction of [wildernessSpacingMetres]: how close a candidate may sit to an already-placed node. */
  val minNodeSeparationFactor: Double = 0.6,

  /** As a fraction of [wildernessSpacingMetres]: how far to look for a node to connect to. */
  val connectRadiusFactor: Double = 1.6,

  /**
   * Most edges one wilderness node may sprout.
   *
   * A cap on the *search*, not on the result: a node can still end up with more edges once its neighbours
   * connect back to it. Six is enough for the lattice to be richly connected without paying for a route
   * search per pair in a dense clump.
   */
  val maxNeighboursPerNode: Int = 6,

  /**
   * [LayerId.CIVILISATION_DISTANCE] beyond which the lattice thins out, in metres.
   *
   * Load-bearing on a large world rather than a nicety. Node count scales with area, so a 4096 km world
   * would otherwise carry sixty-four times the reference world's lattice - and the far wilderness is
   * precisely where that density buys least, because nothing is there to route between.
   */
  val remoteWildernessDistanceMetres: Double = 60_000.0,

  /** Spacing multiplier past [remoteWildernessDistanceMetres]. */
  val remoteSpacingMultiplier: Double = 2.0,

  /** Hard ceiling on lattice nodes. Reported wanted-vs-placed, never silently truncated. */
  val maxWildernessNodes: Int = 20_000,

  /** Below this slope, ground is plain [MovementMode.WALK]. In degrees. */
  val walkableSlopeDegrees: Double = 30.0,

  /**
   * Steepest ground a route may cross at all. In degrees.
   *
   * The requirement's forty-five degrees, made explicit and tunable rather than left to fall out of two
   * unrelated constants agreeing - see the class note on what this can and cannot see.
   */
  val maxSlopeDegrees: Double = 45.0,

  /**
   * Longest run of water a wilderness edge may ford before it is rejected outright, in metres.
   *
   * Has to be read against the cell size, and that is the whole subtlety. This stage runs at a kilometre
   * per cell, so "the route crosses a river in this cell" is one cell wide however narrow the actual
   * channel is - and at the 80 m this started as, *every* river crossing in the world was rejected and
   * rivers became walls no animal could ever ford. How wide the channel really is is sub-cell information
   * this tier does not have.
   *
   * So the limit is above one cell diagonal on purpose: a single crossing is allowed, and what it still
   * refuses is a route that runs *along* a waterway for cell after cell. Open water needs no help from
   * this - [Terms.IMPASSABLE] already keeps the router out of lakes and sea.
   */
  val maxSwimSpanMetres: Double = 1_500.0,

  /**
   * The habitability terms, for the one number this stage must not decide for itself.
   *
   * `waterDischarge` is "there is a river in this cell", and [Terms] already has an answer: the same
   * threshold it uses to place fresh water and to charge a road for crossing one. Inventing a second one
   * here went wrong immediately and silently - the first draft guessed fifty times too high, so no cell in
   * any world ever counted as a river and every ford came out tagged as dry land.
   *
   * Nested and deliberately not settable from a params file, exactly like `SettlementParams.habitability`
   * and for the reason [net.bestia.worldgen.pipeline.WorldParams] gives: two copies of a shared number
   * agree for free until a file sets one of them.
   */
  val habitability: HabitabilityParams = HabitabilityParams(),

  /**
   * Per-metre cost of a made surface: road, street, bridge deck, sea lane.
   *
   * Flat, and deliberately **not** re-derived from [LayerId.MOVEMENT_COST]. That field is the cost a
   * road-*builder* faced picking a line through raw terrain; a finished road has been graded, and charging
   * a cart for the hillside the engineers levelled would double-count the very thing the road exists to
   * remove. Below one per metre because a road is faster than open ground, which is the whole point of
   * having one.
   */
  val infrastructureCostPerMetre: Double = 0.5,

  /**
   * Cells one wilderness-edge search may expand before giving up.
   *
   * Two orders of magnitude below `SettlementStage`'s own limit, because these are short local hops
   * between adjacent lattice nodes rather than roads across a continent. A hop that cannot be found inside
   * this is one the terrain does not really offer.
   */
  val hopExpansionLimit: Int = 4_000
) : Params {

  init {
    require(wildernessSpacingMetres > 0.0) {
      "wildernessSpacingMetres must be positive, was $wildernessSpacingMetres"
    }
    require(minNodeSeparationFactor > 0.0) {
      "minNodeSeparationFactor must be positive, was $minNodeSeparationFactor"
    }
    require(connectRadiusFactor >= minNodeSeparationFactor) {
      "connectRadiusFactor $connectRadiusFactor is below minNodeSeparationFactor $minNodeSeparationFactor, " +
          "so a placed node could never reach the neighbour that displaced it"
    }
    require(maxNeighboursPerNode > 0) { "maxNeighboursPerNode must be positive, was $maxNeighboursPerNode" }
    require(remoteWildernessDistanceMetres >= 0.0) { "remoteWildernessDistanceMetres must not be negative" }
    require(remoteSpacingMultiplier >= 1.0) {
      "remoteSpacingMultiplier must not thin the lattice below the base spacing, was $remoteSpacingMultiplier"
    }
    require(maxWildernessNodes >= 0) { "maxWildernessNodes must not be negative, was $maxWildernessNodes" }
    require(walkableSlopeDegrees in 0.0..90.0) {
      "walkableSlopeDegrees must be an angle, was $walkableSlopeDegrees"
    }
    require(maxSlopeDegrees in walkableSlopeDegrees..90.0) {
      "maxSlopeDegrees $maxSlopeDegrees must be between walkableSlopeDegrees $walkableSlopeDegrees and 90"
    }
    require(maxSwimSpanMetres >= 0.0) { "maxSwimSpanMetres must not be negative, was $maxSwimSpanMetres" }
    require(infrastructureCostPerMetre > 0.0) {
      "infrastructureCostPerMetre must be positive, was $infrastructureCostPerMetre"
    }
    require(hopExpansionLimit > 0) { "hopExpansionLimit must be positive, was $hopExpansionLimit" }
  }

  fun overriddenBy(source: ParamsText.ParamsSource) = copy(
    wildernessSpacingMetres = source.double("wildernessSpacingMetres", wildernessSpacingMetres),
    minNodeSeparationFactor = source.double("minNodeSeparationFactor", minNodeSeparationFactor),
    connectRadiusFactor = source.double("connectRadiusFactor", connectRadiusFactor),
    maxNeighboursPerNode = source.int("maxNeighboursPerNode", maxNeighboursPerNode),
    remoteWildernessDistanceMetres = source.double(
      "remoteWildernessDistanceMetres",
      remoteWildernessDistanceMetres
    ),
    remoteSpacingMultiplier = source.double("remoteSpacingMultiplier", remoteSpacingMultiplier),
    maxWildernessNodes = source.int("maxWildernessNodes", maxWildernessNodes),
    walkableSlopeDegrees = source.double("walkableSlopeDegrees", walkableSlopeDegrees),
    maxSlopeDegrees = source.double("maxSlopeDegrees", maxSlopeDegrees),
    maxSwimSpanMetres = source.double("maxSwimSpanMetres", maxSwimSpanMetres),
    infrastructureCostPerMetre = source.double("infrastructureCostPerMetre", infrastructureCostPerMetre),
    hopExpansionLimit = source.int("hopExpansionLimit", hopExpansionLimit)
  )

  override fun digest() = ParamsDigest()
    .put("wildernessSpacingMetres", wildernessSpacingMetres)
    .put("minNodeSeparationFactor", minNodeSeparationFactor)
    .put("connectRadiusFactor", connectRadiusFactor)
    .put("maxNeighboursPerNode", maxNeighboursPerNode)
    .put("remoteWildernessDistanceMetres", remoteWildernessDistanceMetres)
    .put("remoteSpacingMultiplier", remoteSpacingMultiplier)
    .put("maxWildernessNodes", maxWildernessNodes)
    .put("walkableSlopeDegrees", walkableSlopeDegrees)
    .put("maxSlopeDegrees", maxSlopeDegrees)
    .put("maxSwimSpanMetres", maxSwimSpanMetres)
    .nested("habitability", habitability.digest().value)
    .put("infrastructureCostPerMetre", infrastructureCostPerMetre)
    .put("hopExpansionLimit", hopExpansionLimit)
}

/**
 * Where anything can walk, and what each way costs: the macro navigation graph.
 *
 * Emits one [NavGraph] for the world. Nodes are the places a journey passes through - settlements, town
 * gates, either end of a bridge, cave mouths - plus a thinned lattice over open country so that something
 * *avoiding* roads still has a way across the map. Edges reuse the geometry the civilisation stages already
 * produced and route only the open-country hops themselves.
 *
 * ### Why the roads are not re-routed
 *
 * [SettlementStage] already spent the second-most-expensive pass in the pipeline finding where a road
 * should go, and the answer is stored as a [FeatureKind.ROAD] centerline. Routing them again here would
 * cost the same again to produce a *different* line, and then NPCs would walk beside the road rather than
 * on it. So road, street, bridge and sea-lane edges are read off existing features; the only searching this
 * stage does is between lattice nodes in country no road reaches.
 *
 * ### One graph, every species
 *
 * There is no per-creature graph and no per-creature pass. An edge carries what crossing it *demands*
 * ([MovementMode]) and what it physically *is* ([NavEdgeKind]), and a consumer turns those into a cost for
 * a particular animal - a merchant discounting roads, a wild thing avoiding them. Baking preference in here
 * would mean regenerating the world to retune a monster's behaviour.
 *
 * ### What the slope gate can and cannot see
 *
 * [NavParams.maxSlopeDegrees] is enforced against the [LayerId.ELEVATION] gradient, and this stage runs at
 * kilometre resolution: it sees a mountainside averaged over a kilometre, not the ten-metre cliff inside
 * one cell. So it correctly refuses to route a journey up an alpine face, and it cannot promise that every
 * metre of an accepted edge is under forty-five degrees.
 *
 * That promise is the chunk tier's, and it is already kept there:
 * [net.bestia.worldgen.derived.WalkableTile] admits a step only when the rise is within
 * `AgentProfile.maxStep`, one voxel over one voxel of run - forty-five degrees exactly, per voxel, against
 * the real ground. The two tiers are doing different jobs: this one decides *which valley* to walk up, that
 * one decides whether this particular boulder can be climbed. Neither substitutes for the other, and a
 * consumer that skipped the second because this one "already checked the slope" would walk NPCs off cliffs.
 */
class NavGraphStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: NavParams = NavParams()
) : Stage {

  override val id = ID
  override val version = 1

  override val paramsVersion get() = params.digest().value

  override val dependencies = listOf(
    // DISCHARGE and WATER_LEVEL: which hops are fords and which are simply water.
    HydrologyStage.ID,
    // CIVILISATION_DISTANCE, for thinning the lattice where nothing is.
    CorruptionStage.ID,
    // MOVEMENT_COST for the open-country search, ELEVATION transitively for the slope gate.
    HabitabilityStage.ID,
    // SETTLEMENT, ROAD, BRIDGE, SEA_LANE.
    SettlementStage.ID,
    // Which settlements somebody still lives in.
    HistoryStage.ID,
    // CAVE_ENTRANCE.
    CaveStage.ID,
    // GATE.
    TownStage.ID
  )

  override val scale = StageScale.WORLD

  override val outputs = listOf(StageOutput.Navigation)

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val waterLevel = Grid.from(ctx.layers.float(LayerId.WATER_LEVEL))
    val discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE))
    val civDistance = Grid.from(ctx.layers.float(LayerId.CIVILISATION_DISTANCE))
    val movementCost = Grid.from(ctx.layers.float(LayerId.MOVEMENT_COST))

    val terrain = Terrain(region, metres, elevation, waterLevel, discharge, civDistance, movementCost)
    val builder = GraphBuilder(terrain, params)

    Timings.measure("nav.hubs") { builder.addHubs(ctx, region) }
    Timings.measure("nav.infrastructure") { builder.addInfrastructureEdges(ctx, region) }
    Timings.measure("nav.lattice") { builder.addWildernessLattice() }
    Timings.measure("nav.wildernessEdges") { builder.addWildernessEdges() }

    return StageResult(navGraph = builder.build())
  }

  companion object {
    val ID = StageId("nav_graph")
  }
}

/** The rasters the graph is built against, bundled so the builder's signatures stay readable. */
private class Terrain(
  val region: CellRegion,
  val metresPerCell: Double,
  val elevation: Grid,
  val waterLevel: Grid,
  val discharge: Grid,
  val civDistance: Grid,
  val movementCost: Grid
) {

  val width = region.width
  val height = region.height

  /** Flat cell index for a world position, or -1 when it falls outside the region. */
  fun cellAt(position: Vec2d): Int {
    val x = floor(position.x / metresPerCell).toInt() - region.minX
    val y = floor(position.y / metresPerCell).toInt() - region.minY
    if (x !in 0 until width || y !in 0 until height) return -1
    return y * width + x
  }

  fun centreOf(cell: Int): Vec2d = Vec2d(
    (region.minX + cell % width + 0.5) * metresPerCell,
    (region.minY + cell / width + 0.5) * metresPerCell
  )

  fun isWater(cell: Int): Boolean = !waterLevel.data[cell].isNaN()

  /** Local terrain gradient as a tangent - rise over run - which is what a slope cutoff compares against. */
  fun slopeAt(cell: Int): Double =
    elevation.gradient(cell % width, cell / width, metresPerCell)
}

/**
 * Accumulates nodes and edges, keeping ids dense and edges unique.
 *
 * A class rather than a pile of locals because node identity has to be allocated in one place: every edge
 * refers to nodes by index, so "which index did that settlement get" cannot be re-derived later.
 */
private class GraphBuilder(private val terrain: Terrain, private val params: NavParams) {

  private val nodes = ArrayList<NavNode>()
  private val edges = ArrayList<NavEdge>()

  /** Endpoint pairs already connected, lower index first, so two passes cannot emit the same hop twice. */
  private val connected = HashSet<Long>()

  private val index = NodeIndex(BUCKET_METRES)

  /** Settlement index to its node, for joining gates and sea lanes to the town they belong to. */
  private val bySettlement = HashMap<Int, NavNodeId>()

  private var unattachedBridges = 0

  private val maxSlopeTangent = tan(Math.toRadians(params.maxSlopeDegrees))
  private val walkableSlopeTangent = tan(Math.toRadians(params.walkableSlopeDegrees))

  fun build(): NavGraph = NavGraph(nodes, edges)

  // --- Nodes ---------------------------------------------------------------------------------------

  private fun addNode(
    position: Vec2d,
    kind: NavNodeKind,
    settlementIndex: Int = -1,
    standing: Boolean = true
  ): NavNodeId {
    val id = NavNodeId(nodes.size)
    val cell = terrain.cellAt(position)
    nodes.add(
      NavNode(
        id = id,
        position = position,
        kind = kind,
        settlementIndex = settlementIndex,
        standing = standing,
        civilisationDistance = if (cell >= 0) terrain.civDistance.data[cell] else 0.0
      )
    )
    index.add(position, id)
    return id
  }

  /**
   * Every node that comes from a feature somebody already placed.
   *
   * Villages and hamlets get one too, even though [SettlementStage]'s road network deliberately skips them
   * - a merchant can still be sent to a hamlet, and the lattice pass below gives it a track to arrive on
   * without this needing a special case.
   */
  fun addHubs(ctx: GenContext, region: CellRegion) {
    val world = region.toWorld()
    val abandoned = HashSet<Int>()
    for (feature in ctx.features.query(world)) {
      if (feature.kind != FeatureKind.SETTLEMENT_HISTORY) continue
      val past = feature as? PointMarker ?: continue
      if (past.attribute(HistoryChannels.ABANDONED_YEAR).toInt() != 0) {
        abandoned.add(past.attribute(HistoryChannels.INDEX).toInt())
      }
    }

    // Sorted by settlement index rather than taken in query order: node ids are part of the world's
    // identity, and a spatial index's iteration order is not something to hang that on.
    val settlements = ctx.features.query(world)
      .asSequence()
      .filter { it.kind == FeatureKind.SETTLEMENT }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.attribute(SettlementChannels.INDEX).toInt() }
      .toList()

    for (settlement in settlements) {
      val settlementIndex = settlement.attribute(SettlementChannels.INDEX).toInt()
      val id = addNode(
        position = settlement.position,
        kind = NavNodeKind.SETTLEMENT,
        settlementIndex = settlementIndex,
        standing = settlementIndex !in abandoned
      )
      bySettlement[settlementIndex] = id
    }

    val gates = ctx.features.query(world)
      .asSequence()
      .filter { it.kind == FeatureKind.GATE }
      .filterIsInstance<PointMarker>()
      .sortedWith(compareBy({ it.position.x }, { it.position.y }))
      .toList()

    for (gate in gates) {
      val settlementIndex = gate.attribute(GateChannels.SETTLEMENT).toInt()
      val gateId = addNode(gate.position, NavNodeKind.GATE, settlementIndex = settlementIndex)

      // The gate to its own town centre. A short made-surface hop, so a traveller heading for the town does
      // not have to find its way through the street graph the chunk tier owns.
      bySettlement[settlementIndex]?.let { town ->
        connect(
          gateId,
          town,
          NavEdgeKind.GATE_SPOKE,
          gate.position.distanceTo(nodes[town.value].position),
          setOf(MovementMode.WALK),
          gate.attribute(GateChannels.WIDTH) * 0.5
        )
      }
    }

    val entrances = ctx.features.query(world)
      .asSequence()
      .filter { it.kind == FeatureKind.CAVE_ENTRANCE }
      .filterIsInstance<PointMarker>()
      .sortedWith(compareBy({ it.position.x }, { it.position.y }))
      .toList()

    for (entrance in entrances) {
      addNode(entrance.position, NavNodeKind.CAVE_ENTRANCE)
    }
  }

  // --- Edges from existing geometry ---------------------------------------------------------------

  /**
   * Road, bridge and sea-lane edges, read off the features that already describe them.
   *
   * A road is split at every bridge it carries, so that "the bridge is out" is one edge to block rather
   * than a property hidden in the middle of a twenty-kilometre edge nothing can subdivide later.
   */
  fun addInfrastructureEdges(ctx: GenContext, region: CellRegion) {
    val world = region.toWorld()

    // Roads only. A town's streets are not edges here - see [NavEdgeKind.GATE_SPOKE] for why - and letting
    // them in also broke the bridges: a world has hundreds of street segments, every one of them within a
    // few hundred metres of the town's crossings, so each bridge was picked up by dozens of streets and
    // twelve bridges became sixty-seven bridge edges.
    val roads = ctx.features.query(world)
      .asSequence()
      .filter { it.kind == FeatureKind.ROAD }
      .filterIsInstance<PolylineFeature>()
      .sortedBy { it.id.value }
      .toList()

    val bridges = ctx.features.query(world)
      .asSequence()
      .filter { it.kind == FeatureKind.BRIDGE }
      .filterIsInstance<PointMarker>()
      .sortedBy { it.id.value }
      .toList()

    // A bridge is attached to *every* road that runs over it, not to whichever road is nearest.
    //
    // This looks like double-counting and is not. `SettlementStage` emits one marker per road per crossing,
    // so several roads meeting at one river narrows produce several markers at the same point - in the
    // reference world, twelve markers are five distinct crossings shared two-to-four ways. Each of those
    // roads genuinely needs its own bridge edge, or destroying the crossing would block one road and leave
    // the others running over the water. What has to be deduplicated is the other axis: the same crossing
    // reached twice on the *same* road.
    val attached = HashSet<Int>()
    for ((index, road) in roads.withIndex()) {
      val carried = ArrayList<BridgeOnRoad>()
      for ((bridgeIndex, bridge) in bridges.withIndex()) {
        val arc = nearestArcLength(road.centerline, bridge.position)
        if (road.centerline.pointAt(arc).distanceTo(bridge.position) > BRIDGE_ATTACH_METRES) continue

        attached.add(bridgeIndex)
        val span = bridge.attribute(BridgeChannels.SPAN)
        // Two markers this close on one road are one crossing described twice, not two decks.
        if (carried.any { abs(it.arcLength - arc) < span * 0.5 }) continue
        carried.add(BridgeOnRoad(bridge, arc))
      }

      addRoadChain(road, carried.sortedBy { it.arcLength })
    }

    unattachedBridges = bridges.size - attached.size

    // A crossing no road picked up is one NPCs cannot use and nothing else will mention. Reported rather
    // than dropped quietly, for the reason `TownParams.maxBuildingsPerSettlement` reports its own cap: a
    // silently smaller graph looks exactly like a world that happened to have fewer rivers.
    if (unattachedBridges > 0) {
      println(
        "nav_graph: $unattachedBridges of ${bridges.size} bridge markers lie more than " +
            "${BRIDGE_ATTACH_METRES.toInt()} m from any road and carry no edge"
      )
    }

    for (lane in ctx.features.query(world).filter { it.kind == FeatureKind.SEA_LANE }.sortedBy { it.id.value }) {
      val marker = lane as? MarkerFeature ?: continue
      val stations = marker.stations ?: continue
      val from = stations.sample(stations.channel(SeaLaneChannels.FROM_SETTLEMENT), 0.0).toInt()
      val to = stations.sample(stations.channel(SeaLaneChannels.TO_SETTLEMENT), 0.0).toInt()
      val a = bySettlement[from] ?: continue
      val b = bySettlement[to] ?: continue

      // SWIM only, and no WALK: this is the edge a land animal must not take and a ship must. It is also
      // the one place a "cannot swim" tag does real work at the macro tier.
      connect(
        a,
        b,
        NavEdgeKind.SEA_LANE,
        marker.centerline.length,
        setOf(MovementMode.SWIM),
        waypoints = interior(marker.centerline)
      )
    }
  }

  /**
   * One road as a chain of edges: endpoint, then a pair of approach nodes per bridge, then the far endpoint.
   */
  private fun addRoadChain(road: PolylineFeature, carried: List<BridgeOnRoad>) {
    val centerline = road.centerline
    val kind = NavEdgeKind.ROAD

    var cursor = 0.0
    var previous = snapOrCreate(centerline.pointAt(0.0))

    for (crossing in carried) {
      val span = crossing.bridge.attribute(BridgeChannels.SPAN)
      val halfWidth = crossing.bridge.attribute(BridgeChannels.HALF_WIDTH)
      val nearArc = max(cursor, crossing.arcLength - span * 0.5)
      val farArc = (crossing.arcLength + span * 0.5).coerceAtMost(centerline.length)
      if (farArc <= cursor) continue

      // Snapped to whatever is already there, because a crossing right outside a town's gate puts an
      // approach on top of the settlement's own node - and a fresh node in the same spot would leave the
      // bridge joined to the town by a zero-length segment that `addRoadSegment` then declines to emit,
      // stranding the far bank. The tolerance is far below a deck's span so the two banks stay distinct.
      val nearApproach = approachNode(centerline.pointAt(nearArc))
      val farApproach = approachNode(centerline.pointAt(farArc))

      addRoadSegment(previous, nearApproach, centerline, cursor, nearArc, kind)

      connect(
        nearApproach,
        farApproach,
        NavEdgeKind.BRIDGE,
        farArc - nearArc,
        setOf(MovementMode.WALK),
        halfWidth
      )

      previous = farApproach
      cursor = farArc
    }

    val end = snapOrCreate(centerline.pointAt(centerline.length))
    addRoadSegment(previous, end, centerline, cursor, centerline.length, kind)
  }

  private class BridgeOnRoad(val bridge: PointMarker, val arcLength: Double)

  private fun addRoadSegment(
    from: NavNodeId,
    to: NavNodeId,
    centerline: Polyline,
    fromArc: Double,
    toArc: Double,
    kind: NavEdgeKind
  ) {
    if (from == to) return
    val length = toArc - fromArc
    if (length <= 0.0) return

    connect(
      from,
      to,
      kind,
      length,
      setOf(MovementMode.WALK),
      waypoints = sampleArc(centerline, fromArc, toArc)
    )
  }

  /**
   * The node at a road's end, reusing a settlement's own node when the road ends at one.
   *
   * A road is routed between two settlement centres, so the usual answer is that settlement. The tolerance
   * covers the difference between the centre the router aimed at and the cell centre the route ended on.
   */
  private fun snapOrCreate(position: Vec2d): NavNodeId =
    index.nearest(position, ROAD_ENDPOINT_SNAP_METRES) ?: addNode(position, NavNodeKind.WILDERNESS)

  /** A bridge abutment, reusing a node already at that spot rather than stacking a second one on it. */
  private fun approachNode(position: Vec2d): NavNodeId =
    index.nearest(position, APPROACH_SNAP_METRES) ?: addNode(position, NavNodeKind.BRIDGE_APPROACH)

  // --- The open-country lattice ------------------------------------------------------------------

  /**
   * Nodes over country the roads do not reach.
   *
   * Placed on a lattice and then thinned twice: candidates too close to something already placed are
   * dropped, and the spacing itself widens once [NavParams.remoteWildernessDistanceMetres] from anything
   * civilised. The first keeps the graph from clumping around towns, the second is what stops a large
   * world's node count from following its area.
   */
  fun addWildernessLattice() {
    val spacing = params.wildernessSpacingMetres
    val separation = spacing * params.minNodeSeparationFactor
    val remoteSpacing = spacing * params.remoteSpacingMultiplier

    val region = terrain.region
    val metres = terrain.metresPerCell
    val minX = region.minX * metres
    val minY = region.minY * metres
    val maxX = (region.maxX + 1) * metres
    val maxY = (region.maxY + 1) * metres

    var wanted = 0
    var placed = 0

    // Stepped at the base spacing and thinned by the separation test rather than stepped at the remote
    // spacing directly: the coarse grid has to be a subset of the fine one, or the lattice would shift
    // sideways at the boundary and leave a seam of unconnectable nodes across the map.
    var y = minY + spacing * 0.5
    while (y < maxY) {
      var x = minX + spacing * 0.5
      while (x < maxX) {
        val position = Vec2d(x, y)
        x += spacing

        val cell = terrain.cellAt(position)
        if (cell < 0) continue
        if (terrain.movementCost.data[cell] >= Terms.IMPASSABLE) continue
        if (terrain.isWater(cell)) continue
        if (terrain.slopeAt(cell) > maxSlopeTangent) continue

        // Out in the deep wilderness, keep every other lattice point.
        val remote = terrain.civDistance.data[cell] > params.remoteWildernessDistanceMetres
        val required = if (remote) remoteSpacing * params.minNodeSeparationFactor else separation
        wanted++

        if (!index.isClearOf(position, required)) continue
        if (placed >= params.maxWildernessNodes) continue

        addNode(position, NavNodeKind.WILDERNESS)
        placed++
      }
      y += spacing
    }

    if (placed >= params.maxWildernessNodes) {
      println(
        "nav_graph: wilderness lattice hit the cap - wanted up to $wanted candidates, placed $placed " +
            "(maxWildernessNodes=${params.maxWildernessNodes}); raise the cap or widen wildernessSpacingMetres"
      )
    }
  }

  /**
   * Hops between lattice nodes, routed over the movement-cost field.
   *
   * The only searching this stage does. Each hop is a bounded local A* - the neighbours are a spacing
   * apart, not a continent - and the searches are independent, so they run across every core the way
   * [SettlementStage]'s own road routing does.
   */
  fun addWildernessEdges() {
    val radius = params.wildernessSpacingMetres * params.connectRadiusFactor

    // Candidate pairs first, in a deterministic order, then route them in parallel. Building the pair list
    // serially is what keeps the result independent of how the pool interleaves - the searches themselves
    // write nothing shared.
    val pairs = ArrayList<Pair<NavNodeId, NavNodeId>>()
    for (node in nodes) {
      if (node.kind != NavNodeKind.WILDERNESS) continue

      val neighbours = index.within(node.position, radius)
        .asSequence()
        .filter { it != node.id }
        .sortedBy { nodes[it.value].position.distanceSquaredTo(node.position) }
        .take(params.maxNeighboursPerNode)

      for (neighbour in neighbours) {
        val key = pairKey(node.id, neighbour)
        if (!connected.add(key)) continue
        pairs.add(node.id to neighbour)
      }
    }

    val routed = Parallel.map(pairs.size) { i ->
      val (a, b) = pairs[i]
      route(nodes[a.value], nodes[b.value])
    }

    for ((i, hop) in routed.withIndex()) {
      if (hop == null) continue
      val (a, b) = pairs[i]
      edges.add(
        NavEdge(
          a = a,
          b = b,
          kind = NavEdgeKind.WILDERNESS,
          lengthMetres = hop.lengthMetres,
          baseCost = hop.cost,
          modes = hop.modes,
          waypoints = hop.waypoints
        )
      )
    }
  }

  /** A routed open-country hop, or null when the terrain does not offer one. */
  private class Hop(
    val lengthMetres: Double,
    val cost: Double,
    val modes: Set<MovementMode>,
    val waypoints: List<Vec2d>?
  )

  private fun route(from: NavNode, to: NavNode): Hop? {
    val start = terrain.cellAt(from.position)
    val goal = terrain.cellAt(to.position)
    if (start < 0 || goal < 0 || start == goal) return null

    val finder = RouteFinder(
      cost = terrain.movementCost,
      metresPerCell = terrain.metresPerCell,
      expansionLimit = params.hopExpansionLimit
    )
    val route = finder.route(start, goal) ?: return null

    val modes = HashSet<MovementMode>()
    var length = 0.0
    var waterRun = 0.0

    for (i in 1 until route.cells.size) {
      val previous = route.cells[i - 1]
      val cell = route.cells[i]

      val step = stepMetres(previous, cell)
      length += step

      // Averaged over the step rather than taken from one cell, because a step between two cells is a slope
      // between two elevations - and the gradient at a cell centre says something about its neighbourhood,
      // not about this particular crossing.
      val rise = abs(terrain.elevation.data[cell] - terrain.elevation.data[previous])
      val slope = if (step > 0.0) rise / step else 0.0
      val localSlope = max(terrain.slopeAt(cell), terrain.slopeAt(previous))

      // The cutoff. An edge over ground this steep is not generated at all, which is what makes
      // "too steep to walk" a property of the graph rather than a cost a desperate search can pay.
      if (slope > maxSlopeTangent || localSlope > maxSlopeTangent) return null

      if (slope > walkableSlopeTangent || localSlope > walkableSlopeTangent) {
        modes.add(MovementMode.CLIMB)
      }

      val wet = terrain.isWater(cell) || terrain.discharge.data[cell] >= params.habitability.waterDischarge
      if (wet) {
        waterRun += step
        // Past a ford's width this is not a crossing an animal wades - it needs a bridge or a boat, and
        // both of those are separately modelled edges that already exist where they belong.
        if (waterRun > params.maxSwimSpanMetres) return null
        modes.add(MovementMode.SWIM)
      } else {
        waterRun = 0.0
        modes.add(MovementMode.WALK)
      }
    }

    if (modes.isEmpty()) return null

    return Hop(
      lengthMetres = length,
      cost = route.cost,
      modes = modes,
      waypoints = waypointsOf(route.cells)
    )
  }

  /**
   * Centre-to-centre distance of one route step.
   *
   * Through [D8.LENGTH] rather than a local square root, so a diagonal costs what it costs everywhere else
   * in the pipeline - `RouteFinder` charged this same step by the same table when it found the route.
   */
  private fun stepMetres(from: Int, to: Int): Double {
    val dx = abs(from % terrain.width - to % terrain.width)
    val dy = abs(from / terrain.width - to / terrain.width)
    val direction = D8.DX.indices.firstOrNull { abs(D8.DX[it]) == dx && abs(D8.DY[it]) == dy }
    return terrain.metresPerCell * (direction?.let { D8.LENGTH[it] } ?: 1.0)
  }

  /** Interior cells as world positions. The endpoints are the nodes themselves and are not repeated. */
  private fun waypointsOf(cells: IntArray): List<Vec2d>? {
    if (cells.size <= 2) return null
    return (1 until cells.size - 1).map { terrain.centreOf(cells[it]) }
  }

  // --- Shared -------------------------------------------------------------------------------------

  private fun connect(
    a: NavNodeId,
    b: NavNodeId,
    kind: NavEdgeKind,
    lengthMetres: Double,
    modes: Set<MovementMode>,
    maxAgentHalfWidth: Double = Double.MAX_VALUE,
    waypoints: List<Vec2d>? = null
  ) {
    if (a == b) return
    if (!connected.add(pairKey(a, b))) return

    edges.add(
      NavEdge(
        a = a,
        b = b,
        kind = kind,
        lengthMetres = lengthMetres,
        baseCost = lengthMetres * params.infrastructureCostPerMetre,
        modes = modes,
        maxAgentHalfWidth = maxAgentHalfWidth,
        waypoints = waypoints
      )
    )
  }

  private fun pairKey(a: NavNodeId, b: NavNodeId): Long {
    val low = minOf(a.value, b.value).toLong()
    val high = maxOf(a.value, b.value).toLong()
    return (low shl 32) or high
  }

  /** Arc length of the point on [line] nearest [point], found by sampling then refining around the best. */
  private fun nearestArcLength(line: Polyline, point: Vec2d): Double {
    val coarseStep = (line.length / COARSE_SAMPLES).coerceAtLeast(1.0)
    var best = 0.0
    var bestDistance = Double.MAX_VALUE

    var s = 0.0
    while (s <= line.length) {
      val d = line.pointAt(s).distanceSquaredTo(point)
      if (d < bestDistance) {
        bestDistance = d
        best = s
      }
      s += coarseStep
    }

    var window = coarseStep
    repeat(REFINEMENT_PASSES) {
      window *= 0.5
      for (candidate in listOf(best - window, best + window)) {
        if (candidate < 0.0 || candidate > line.length) continue
        val d = line.pointAt(candidate).distanceSquaredTo(point)
        if (d < bestDistance) {
          bestDistance = d
          best = candidate
        }
      }
    }

    return best
  }

  /** The polyline between two arc lengths, sampled at the vertex spacing the line already has. */
  private fun sampleArc(line: Polyline, fromArc: Double, toArc: Double): List<Vec2d>? {
    val span = toArc - fromArc
    if (span <= 0.0) return null

    val steps = (span / WAYPOINT_SPACING_METRES).toInt()
    if (steps <= 1) return null

    return (1 until steps).map { line.pointAt(fromArc + span * it / steps) }
  }

  private fun interior(line: Polyline): List<Vec2d>? {
    val points = line.points
    return if (points.size <= 2) null else points.subList(1, points.size - 1).toList()
  }

  companion object {
    /** Bucket edge for the node index. Must be at least the widest radius any query uses. */
    private const val BUCKET_METRES = 20_000.0

    /**
     * How far a road end may be from a settlement centre and still be that settlement.
     *
     * A road is routed to the settlement's cell, so the gap is at most a cell diagonal plus whatever the
     * grading moved. Generous rather than tight: creating a second node a hundred metres from a town's own
     * would leave the road joined to a stub the town is not on.
     */
    private const val ROAD_ENDPOINT_SNAP_METRES = 1_500.0

    /** How close a bridge marker must be to a road's centerline to count as carried by it. */
    private const val BRIDGE_ATTACH_METRES = 200.0

    /**
     * How close an abutment may be to an existing node and be that node.
     *
     * Well under the shortest deck span the settlement stage emits, so the two banks of one bridge are
     * never merged into a single node - which would turn the crossing into a self-loop and delete it.
     */
    private const val APPROACH_SNAP_METRES = 50.0

    /** Spacing of the waypoints a road edge hands the local tier, in metres. */
    private const val WAYPOINT_SPACING_METRES = 250.0

    private const val COARSE_SAMPLES = 256
    private const val REFINEMENT_PASSES = 12
  }
}

/**
 * A bucket grid over placed nodes, for "what is near here" during construction.
 *
 * The same shape and the same reasoning as `SettlementStage`'s separation index: placement asks the
 * question once per candidate over a quarter of a million candidates, and the quadratic version of that is
 * the longest loop in the stage.
 */
private class NodeIndex(private val bucketMetres: Double) {

  private val buckets = HashMap<Long, ArrayList<Entry>>()

  private class Entry(val position: Vec2d, val id: NavNodeId)

  fun add(position: Vec2d, id: NavNodeId) {
    buckets.getOrPut(keyOf(position)) { ArrayList() }.add(Entry(position, id))
  }

  fun isClearOf(position: Vec2d, minDistance: Double): Boolean {
    val minSq = minDistance * minDistance
    return visit(position, minDistance) { it.position.distanceSquaredTo(position) < minSq } == null
  }

  fun nearest(position: Vec2d, maxDistance: Double): NavNodeId? {
    val limitSq = maxDistance * maxDistance
    var best: NavNodeId? = null
    var bestSq = Double.MAX_VALUE

    forEachNear(position, maxDistance) { entry ->
      val d = entry.position.distanceSquaredTo(position)
      if (d <= limitSq && d < bestSq) {
        bestSq = d
        best = entry.id
      }
    }

    return best
  }

  fun within(position: Vec2d, radius: Double): List<NavNodeId> {
    val limitSq = radius * radius
    val found = ArrayList<NavNodeId>()
    forEachNear(position, radius) { entry ->
      if (entry.position.distanceSquaredTo(position) <= limitSq) found.add(entry.id)
    }
    // By id, so a caller that only sorts by distance still gets a stable order among equal distances.
    return found.sortedBy { it.value }
  }

  /** Returns the first entry matching [predicate], or null. Short-circuits, unlike [forEachNear]. */
  private inline fun visit(position: Vec2d, radius: Double, predicate: (Entry) -> Boolean): Entry? {
    val reach = radiusInBuckets(radius)
    val bx = floor(position.x / bucketMetres).toLong()
    val by = floor(position.y / bucketMetres).toLong()

    for (dy in -reach..reach) {
      for (dx in -reach..reach) {
        val bucket = buckets[key(bx + dx, by + dy)] ?: continue
        for (entry in bucket) {
          if (predicate(entry)) return entry
        }
      }
    }

    return null
  }

  private inline fun forEachNear(position: Vec2d, radius: Double, body: (Entry) -> Unit) {
    val reach = radiusInBuckets(radius)
    val bx = floor(position.x / bucketMetres).toLong()
    val by = floor(position.y / bucketMetres).toLong()

    for (dy in -reach..reach) {
      for (dx in -reach..reach) {
        val bucket = buckets[key(bx + dx, by + dy)] ?: continue
        for (entry in bucket) body(entry)
      }
    }
  }

  /**
   * How many buckets out a radius reaches.
   *
   * Computed rather than fixed at one, because a query wider than a bucket would otherwise silently miss
   * everything past the first ring - the sort of bug that shows up as a graph that is subtly less connected
   * than it should be, on one world size only.
   */
  private fun radiusInBuckets(radius: Double): Int =
    Math.ceil(radius / bucketMetres).toInt().coerceAtLeast(1)

  private fun keyOf(position: Vec2d) =
    key(floor(position.x / bucketMetres).toLong(), floor(position.y / bucketMetres).toLong())

  private fun key(bx: Long, by: Long) = (bx shl 32) or (by and 0xFFFFFFFFL)
}
