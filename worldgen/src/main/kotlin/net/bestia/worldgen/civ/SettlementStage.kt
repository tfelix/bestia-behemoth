package net.bestia.worldgen.civ

import net.bestia.worldgen.bio.Biome
import net.bestia.worldgen.bio.BiomeStage
import net.bestia.worldgen.climate.ClimateStage
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
import net.bestia.worldgen.fields.Grid
import net.bestia.worldgen.geo.ErosionStage
import net.bestia.worldgen.hydro.HydrologyStage
import net.bestia.worldgen.resource.ResourceStage
import net.bestia.worldgen.vector.BlendMode
import net.bestia.worldgen.vector.FeatureId
import net.bestia.worldgen.vector.FeatureKind
import net.bestia.worldgen.vector.Intersections
import net.bestia.worldgen.vector.LinearFeatures
import net.bestia.worldgen.vector.PointFeature
import net.bestia.worldgen.vector.PointMarker
import net.bestia.worldgen.vector.Polyline
import net.bestia.worldgen.vector.PolylineFeature
import net.bestia.worldgen.vector.Profiles
import net.bestia.worldgen.vector.RadialProfiles
import net.bestia.worldgen.vector.StationTable
import net.bestia.worldgen.vector.Vec2d
import net.bestia.worldgen.vector.VectorFeature
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Tuning for [SettlementStage]. */
data class SettlementParams(

  /** The habitability terms are recomputed here; these must match the habitability stage's. */
  val habitability: HabitabilityParams = HabitabilityParams(),

  /** Square kilometres of world per city. Everything else is derived from the city count. */
  val areaPerCity: Double = 26_000.0,

  val townsPerCity: Double = 3.2,
  val villagesPerTown: Double = 2.8,
  val hamletsPerVillage: Double = 1.8,

  /** Habitability below which nobody settles at all, whatever the separation rules allow. */
  val minHabitability: Double = 0.30,

  /**
   * How much a network position is worth on top of raw habitability.
   *
   * Confluences, river mouths, harbours, mountain passes and biome boundaries are chokepoints and trade
   * junctions, and they produce real cities out of all proportion to how good the farmland is. Without this
   * term, placement puts everything on the best soil and the map has no reason for anywhere to be important.
   */
  val networkBonus: Double = 0.45,

  /** Station spacing along a road centerline, in metres. */
  val roadSpacing: Double = 60.0,

  /** Metres a settlement's grading may cut down, and fill up. */
  val maxCut: Double = 9.0,
  val maxFill: Double = 2.5,

  /** Widest river a road will bridge. Anything wider needs a ferry, which is not a vector feature. */
  val maxBridgeSpan: Double = 260.0
)

/**
 * Stage 8: settlements, the trade network, and the roads that realise it.
 *
 * Placement is greedy over habitability with a minimum separation per tier, biased towards *network*
 * positions rather than only towards good land. Cities then get a culture chosen by scoring their own site
 * with each culture's weights - so a sheltered inlet founds a seafaring city and an ore field founds a
 * highland one, and no culture map has to be generated separately.
 *
 * Roads are the payoff of everything in the vector tier. They are routed with A* over the movement cost
 * field, pruned to a Gabriel graph so the network is sparse rather than complete, tiered by simulated
 * traffic, and then stamped exactly like a river channel - the same centerline, the same station table, the
 * same profile evaluation, a different cross section. That is what the architecture document means by
 * "roads reuse the vector machinery from step 2 with no new code".
 *
 * Road-river crossings are found as geometric intersections between the two sets of polylines and emitted as
 * [FeatureKind.BRIDGE] markers, so every chunk along a bridge agrees that it is there and where its
 * abutments are.
 */
class SettlementStage(
  override val resolution: Resolution = Resolution.KILOMETRE,
  private val params: SettlementParams = SettlementParams()
) : Stage {

  override val id = ID
  override val version = 1
  override val dependencies = listOf(
    ClimateStage.ID, ErosionStage.ID, HydrologyStage.ID, BiomeStage.ID,
    ResourceStage.ID, HabitabilityStage.ID
  )
  override val scale = StageScale.WORLD

  override val outputs = listOf(
    StageOutput.Vector(FeatureKind.SETTLEMENT),
    StageOutput.Vector(FeatureKind.SETTLEMENT_GRADING),
    StageOutput.Vector(FeatureKind.ROAD),
    StageOutput.Vector(FeatureKind.BRIDGE)
  )

  /** A placed settlement, before it becomes features. */
  private class Site(
    val cell: Int,
    val position: Vec2d,
    val tier: SettlementTier,
    val culture: Culture,
    val habitability: Double,
    val elevation: Double,
    val population: Int
  )

  override fun generate(ctx: GenContext, region: CellRegion): StageResult {
    val metres = region.resolution.metresPerCell
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val habitability = Grid.from(ctx.layers.float(LayerId.HABITABILITY))
    val movementCost = Grid.from(ctx.layers.float(LayerId.MOVEMENT_COST))
    val terms = Terms.read(ctx, region, params.habitability)

    val rivers = ctx.features.query(region.toWorld())
      .filterIsInstance<PolylineFeature>()
      .filter { it.kind == FeatureKind.RIVER_CHANNEL }

    val siteScore = scoreSites(ctx, region, habitability, terms, rivers)
    val sites = place(ctx, region, siteScore, terms, elevation, metres)

    val nextId = FeatureIds.allocator(id)
    val features = ArrayList<VectorFeature>()

    for (site in sites) {
      features.add(settlementMarker(nextId(), site))
      features.add(gradingFor(nextId(), site))
    }

    features.addAll(buildRoads(ctx, region, sites, movementCost, elevation, rivers, terms.submerged, nextId))

    return StageResult(features = features)
  }

  // --- Placement -------------------------------------------------------------------------------------

  /**
   * Habitability lifted by how good a *network position* each cell is.
   *
   * The five bonuses are the ones that historically decide where a city ends up rather than a village: a
   * confluence, a river mouth, a sheltered harbour, a mountain pass, and the boundary between two biomes -
   * because two biomes side by side means two things to trade.
   */
  private fun scoreSites(
    ctx: GenContext,
    region: CellRegion,
    habitability: Grid,
    terms: Terms,
    rivers: List<PolylineFeature>
  ): Grid {
    val metres = region.resolution.metresPerCell
    val discharge = Grid.from(ctx.layers.float(LayerId.DISCHARGE))
    val elevation = Grid.from(ctx.layers.float(LayerId.ELEVATION))
    val biome = ctx.layers.int(LayerId.BIOME)

    val network = Grid(region.width, region.height)

    // Confluences and mouths come straight out of the river graph the hydrology stage already built - they
    // are explicit features, so there is nothing to detect.
    for (feature in ctx.features.query(region.toWorld())) {
      when (feature.kind) {
        FeatureKind.RIVER_CONFLUENCE -> stamp(network, region, feature.bbox.centerX, feature.bbox.centerY, CONFLUENCE_RANGE, 1.0)
        FeatureKind.RIVER_CHANNEL -> {
          val river = feature as? PolylineFeature ?: continue
          // The mouth is the downstream end of a reach that finishes at the sea.
          val mouth = river.centerline.points.last()
          val cell = cellOf(region, mouth, metres) ?: continue
          if (!terms.submerged[cell] && discharge.data[cell] >= MOUTH_DISCHARGE) {
            stamp(network, region, mouth.x, mouth.y, MOUTH_RANGE, 0.9)
          }
        }
        else -> Unit
      }
    }

    for (y in 0 until region.height) {
      for (x in 0 until region.width) {
        val i = y * region.width + x
        if (terms.submerged[i]) continue

        // A harbour is already a term of habitability; here it counts again as a *trade* position, which is
        // a different thing - a port with poor land is still worth founding.
        network.data[i] = max(network.data[i], terms.harbour.data[i])

        // A pass: low ground with high ground on two opposing sides. The only way through a range, which is
        // why a fort or a market ends up on it.
        network.data[i] = max(network.data[i], passQuality(elevation, x, y, metres))

        // A biome boundary means two different products within a day's walk.
        if (isBiomeBoundary(biome, region, x, y)) {
          network.data[i] = max(network.data[i], BIOME_EDGE_BONUS)
        }
      }
    }

    val score = Grid(region.width, region.height)
    for (i in score.data.indices) {
      if (terms.submerged[i]) continue
      score.data[i] = habitability.data[i] * (1.0 + params.networkBonus * network.data[i])
    }

    return score
  }

  /**
   * Greedy selection, tier by tier from the largest down.
   *
   * A settlement must clear its own tier's separation from every settlement of that tier *or larger*. Smaller
   * places are free to cluster around a city, which is what villages do; two cities forty kilometres apart is
   * what does not happen.
   */
  private fun place(
    ctx: GenContext,
    region: CellRegion,
    score: Grid,
    terms: Terms,
    elevation: Grid,
    metres: Double
  ): List<Site> {
    val bounds = region.toWorld()
    val areaSquareKm = bounds.width * bounds.height / 1_000_000.0
    val cityTarget = max(1, (areaSquareKm / params.areaPerCity).toInt())

    val targets = mapOf(
      SettlementTier.CITY to cityTarget,
      SettlementTier.TOWN to (cityTarget * params.townsPerCity).toInt(),
      SettlementTier.VILLAGE to (cityTarget * params.townsPerCity * params.villagesPerTown).toInt(),
      SettlementTier.HAMLET to
          (cityTarget * params.townsPerCity * params.villagesPerTown * params.hamletsPerVillage).toInt()
    )

    // Sorted once. Ties broken by cell index so the order is total and reproducible.
    val candidates = (0 until score.size)
      .filter { !terms.submerged[it] && score.data[it] >= params.minHabitability }
      .sortedWith(compareByDescending<Int> { score.data[it] }.thenBy { it })

    val placed = ArrayList<Site>()
    val rng = ctx.rng(POPULATION_STREAM)

    for (tier in SettlementTier.entries) {
      val target = targets.getValue(tier)
      if (target <= 0) continue

      val index = SeparationIndex(tier.separation)
      for (site in placed) {
        if (site.tier.ordinal <= tier.ordinal) index.add(site.position)
      }

      var count = 0
      for (cell in candidates) {
        if (count >= target) break

        val position = centreOf(region, cell, metres)
        if (!index.isClearOf(position, tier.separation)) continue

        val quality = score.data[cell].coerceIn(0.0, 1.0)
        val culture = Culture.ALL.maxByOrNull { terms.scoreAt(cell, it) } ?: Culture.AGRARIAN

        placed.add(
          Site(
            cell = cell,
            position = position,
            tier = tier,
            culture = culture,
            habitability = quality,
            elevation = elevation.data[cell],
            population = populationFor(tier, quality, rng)
          )
        )
        index.add(position)
        count++
      }
    }

    return placed
  }

  private fun populationFor(tier: SettlementTier, quality: Double, rng: GenRng): Int {
    val span = tier.maxPopulation - tier.minPopulation
    // Quality decides most of it, with enough jitter that two equally-sited towns are not twins.
    val fraction = (quality * 0.75 + rng.nextDouble() * 0.25).coerceIn(0.0, 1.0)
    return tier.minPopulation + (span * fraction).toInt()
  }

  /**
   * How much this cell looks like a mountain pass: low ground flanked by high ground on opposite sides.
   *
   * Checked along both axes and both diagonals. A saddle scores on one axis and not the other, which is
   * exactly what distinguishes it from a pit - a pit is enclosed on all four.
   */
  private fun passQuality(elevation: Grid, x: Int, y: Int, metresPerCell: Double): Double {
    val here = elevation[x, y]
    val reach = max(2, (3_000.0 / metresPerCell).toInt())
    var best = 0.0

    for (axis in 0 until 4) {
      val dx = PASS_DX[axis]
      val dy = PASS_DY[axis]
      val up = elevation[x + dx * reach, y + dy * reach] - here
      val down = elevation[x - dx * reach, y - dy * reach] - here
      if (up <= 0.0 || down <= 0.0) continue

      // The through direction has to be genuinely lower, or this is a valley floor rather than a pass.
      val across = min(up, down)
      val alongA = elevation[x + dy * reach, y - dx * reach] - here
      val alongB = elevation[x - dy * reach, y + dx * reach] - here
      if (alongA > across * 0.5 && alongB > across * 0.5) continue

      best = max(best, (across / PASS_RELIEF).coerceIn(0.0, 1.0))
    }

    return best
  }

  private fun isBiomeBoundary(
    biome: net.bestia.worldgen.core.IntLayer,
    region: CellRegion,
    x: Int,
    y: Int
  ): Boolean {
    val here = biome[region.minX + x, region.minY + y]
    for (d in 0 until 4) {
      val other = biome[region.minX + x + PASS_DX[d], region.minY + y + PASS_DY[d]]
      if (other != here && !Biome.of(other).isWater && !Biome.of(here).isWater) return true
    }
    return false
  }

  // --- Features ------------------------------------------------------------------------------------

  private fun settlementMarker(id: FeatureId, site: Site) = PointMarker(
    id = id,
    kind = FeatureKind.SETTLEMENT,
    position = site.position,
    attributes = StationTable.Builder(1)
      .channel(SettlementChannels.TIER) { site.tier.ordinal.toDouble() }
      .channel(SettlementChannels.CULTURE) { Culture.ALL.indexOf(site.culture).toDouble() }
      .channel(SettlementChannels.POPULATION) { site.population.toDouble() }
      .channel(SettlementChannels.HABITABILITY) { site.habitability }
      .channel(SettlementChannels.ELEVATION) { site.elevation }
      .build()
  )

  /**
   * The terrain grading under a settlement: a soft terrace towards the site elevation.
   *
   * Asymmetric on purpose - it may cut a good deal and fill very little. Real earthworks cut more than they
   * fill, and more importantly a generous fill limit would let a riverside town raise the channel running
   * through it to street level, because grading is stamped after the river. Limiting fill to a couple of
   * metres leaves the channel intact while still levelling the ground the buildings stand on.
   */
  private fun gradingFor(id: FeatureId, site: Site) = PointFeature(
    id = id,
    kind = FeatureKind.SETTLEMENT_GRADING,
    center = site.position,
    radius = site.tier.footprintRadius,
    profile = RadialProfiles.terrace(site.elevation, params.maxCut, params.maxFill),
    edgeFraction = 0.6,
    blend = BlendMode.REPLACE
  )

  // --- Roads ---------------------------------------------------------------------------------------

  /**
   * The trade network: which settlements are connected, by what route, and how busy it is.
   *
   * Only cities and towns take part. Villages and hamlets are agricultural fill around them and are reached
   * by tracks nobody surveys; connecting every hamlet would produce a road network with more mileage than
   * the terrain, and none of it meaning anything.
   */
  private fun buildRoads(
    ctx: GenContext,
    region: CellRegion,
    sites: List<Site>,
    movementCost: Grid,
    elevation: Grid,
    rivers: List<PolylineFeature>,
    submerged: BooleanArray,
    nextId: () -> FeatureId
  ): List<VectorFeature> {
    val metres = region.resolution.metresPerCell
    val nodes = sites.filter { it.tier == SettlementTier.CITY || it.tier == SettlementTier.TOWN }
    if (nodes.size < 2) return emptyList()

    val edges = gabrielEdges(nodes)
    if (edges.isEmpty()) return emptyList()

    val finder = RouteFinder(movementCost, metres)
    val routes = LinkedHashMap<Pair<Int, Int>, RouteFinder.Route>()
    for ((a, b) in edges) {
      val route = finder.route(nodes[a].cell, nodes[b].cell) ?: continue

      // Reject any route that goes to sea. Water is expensive in the cost field rather than forbidden -
      // finite cost is what lets A* find a way round a lake - but that also means it will happily run a
      // road straight across an ocean when there is no land route at all. Two settlements on different
      // landmasses are simply not road-connected; they would be connected by a sea lane, which is a
      // different kind of feature and is not generated.
      if (route.cells.any { submerged[it] }) continue

      routes[a to b] = route
    }
    if (routes.isEmpty()) return emptyList()

    val traffic = simulateTraffic(nodes, routes)
    val out = ArrayList<VectorFeature>()

    for ((pair, route) in routes) {
      if (route.length < 3) continue

      val points = route.cells.map { centreOf(region, it, metres) }
      val raw = runCatching { Polyline(points) }.getOrNull() ?: continue
      // Corner cutting for the same reason rivers need it: an eight-direction cell path reads as a canal.
      val centerline = raw.chaikin(ROAD_SMOOTHING).resample(params.roadSpacing)

      val tier = roadTierOf(traffic[pair] ?: 0.0)
      val crossings = riverCrossings(centerline, rivers)

      out.add(roadFeature(nextId(), centerline, tier, elevation, crossings))
      for (crossing in crossings) {
        out.add(bridgeMarker(nextId(), centerline, crossing, tier, elevation))
      }
    }

    return out
  }

  /**
   * Gabriel graph: keep an edge only if no third settlement lies inside the circle having that edge as its
   * diameter.
   *
   * Without pruning, connecting every pair gives a complete graph - every city with a direct road to every
   * other, which is neither how road networks look nor how they were built. The Gabriel condition keeps
   * exactly the edges between places that are *mutually nearest* in the relevant sense, and produces the
   * sparse triangulated look of a real trunk network.
   */
  private fun gabrielEdges(nodes: List<Site>): List<Pair<Int, Int>> {
    val edges = ArrayList<Pair<Int, Int>>()

    for (a in nodes.indices) {
      for (b in a + 1 until nodes.size) {
        val midX = (nodes[a].position.x + nodes[b].position.x) * 0.5
        val midY = (nodes[a].position.y + nodes[b].position.y) * 0.5
        val radius = nodes[a].position.distanceTo(nodes[b].position) * 0.5
        val radiusSq = radius * radius

        var blocked = false
        for (c in nodes.indices) {
          if (c == a || c == b) continue
          val dx = nodes[c].position.x - midX
          val dy = nodes[c].position.y - midY
          if (dx * dx + dy * dy < radiusSq) {
            blocked = true
            break
          }
        }

        if (!blocked) edges.add(a to b)
      }
    }

    return edges
  }

  /**
   * Traffic on each edge, from a gravity model over shortest paths through the pruned network.
   *
   * Every pair of settlements trades in proportion to the product of their populations and against the cost
   * of getting between them, and that trade travels along the network. Summing it per edge is what makes some
   * roads highways and others tracks - and it makes the busy ones the ones that connect big places *through*
   * somewhere, which is how a market town becomes a city.
   */
  private fun simulateTraffic(
    nodes: List<Site>,
    routes: Map<Pair<Int, Int>, RouteFinder.Route>
  ): Map<Pair<Int, Int>, Double> {
    val n = nodes.size
    val distance = Array(n) { DoubleArray(n) { Double.MAX_VALUE / 4 } }
    val next = Array(n) { IntArray(n) { -1 } }

    for (i in 0 until n) {
      distance[i][i] = 0.0
      next[i][i] = i
    }
    for ((pair, route) in routes) {
      val (a, b) = pair
      distance[a][b] = route.cost
      distance[b][a] = route.cost
      next[a][b] = b
      next[b][a] = a
    }

    // Floyd-Warshall. A few dozen nodes, so the cubic cost is nothing, and it gives path reconstruction for
    // free - which is what the traffic accumulation needs.
    for (k in 0 until n) {
      for (i in 0 until n) {
        for (j in 0 until n) {
          val through = distance[i][k] + distance[k][j]
          if (through < distance[i][j]) {
            distance[i][j] = through
            next[i][j] = next[i][k]
          }
        }
      }
    }

    val traffic = HashMap<Pair<Int, Int>, Double>()
    for (i in 0 until n) {
      for (j in i + 1 until n) {
        if (next[i][j] < 0) continue
        val flow = nodes[i].population.toDouble() * nodes[j].population /
            (1_000_000.0 * max(1.0, distance[i][j] / 1_000.0))

        var at = i
        var guard = 0
        while (at != j && guard++ < n) {
          val step = next[at][j]
          if (step < 0) break
          val key = if (at < step) at to step else step to at
          if (key in routes) traffic[key] = (traffic[key] ?: 0.0) + flow
          at = step
        }
      }
    }

    return traffic
  }

  private fun roadTierOf(traffic: Double): RoadTier = when {
    traffic >= HIGHWAY_TRAFFIC -> RoadTier.HIGHWAY
    traffic >= ROAD_TRAFFIC -> RoadTier.ROAD
    else -> RoadTier.TRACK
  }

  /** Road surface class: width, and how much earthwork it justifies. */
  private enum class RoadTier(val halfWidth: Double, val shoulder: Double) {
    TRACK(1.6, 3.0),
    ROAD(3.0, 6.0),
    HIGHWAY(5.0, 11.0)
  }

  /**
   * Where a road crosses a river, and how wide the channel is there.
   *
   * Crossings wider than the bridging limit are dropped rather than bridged: a five hundred metre river needs
   * a ferry, and pretending a road spans it would put a causeway across a major waterway.
   */
  private fun riverCrossings(
    road: Polyline,
    rivers: List<PolylineFeature>
  ): List<Crossing> {
    val out = ArrayList<Crossing>()

    for (river in rivers) {
      if (!road.bbox.intersects(river.bbox)) continue

      val widthChannel = runCatching { river.stations.channel(Profiles.CHANNEL_WIDTH) }.getOrNull() ?: continue
      val depthChannel = runCatching { river.stations.channel(Profiles.CHANNEL_DEPTH) }.getOrNull() ?: continue

      for (hit in Intersections.of(road, river.centerline)) {
        val u = river.centerline.stationParamAt(hit.sB)
        val span = river.stations.sample(widthChannel, u)
        if (span > params.maxBridgeSpan) continue

        out.add(Crossing(hit.sA, hit.point, span, river.stations.sample(depthChannel, u)))
      }
    }

    return out.sortedBy { it.roadArcLength }
  }

  private class Crossing(
    val roadArcLength: Double,
    val point: Vec2d,
    val channelWidth: Double,
    val channelDepth: Double
  )

  /**
   * The road as a stamped feature, with a gap at every bridged crossing.
   *
   * The gap is the point. A road is stamped with [BlendMode.REPLACE] and at a higher priority than the river,
   * so without it the carriageway would overwrite the channel and dam the river at every crossing. Setting
   * the corridor half-width to zero over the channel leaves the water flowing and the road interrupted -
   * which is a ford, and is what a crossing without a deck actually is. The deck itself is a voxel structure
   * rather than a heightfield one, because a heightfield has one height per column and cannot express a
   * surface with air beneath it; the [FeatureKind.BRIDGE] marker records where it goes.
   */
  private fun roadFeature(
    id: FeatureId,
    centerline: Polyline,
    tier: RoadTier,
    elevation: Grid,
    crossings: List<Crossing>
  ): PolylineFeature {
    // Both the carriageway *and* the shoulder have to go to zero over a crossing. Zeroing only the carriageway
    // is not enough and is a subtle trap: the corridor half-width that decides whether the feature evaluates at
    // all is the sum of the two, so a road with no carriageway and a six metre shoulder still stamps - and the
    // shoulder profile eases from the road surface at the centreline, which is exactly the channel-filling this
    // gap exists to prevent.
    fun inGap(s: Double) = crossings.any {
      kotlin.math.abs(s - it.roadArcLength) < it.channelWidth * 0.5 + BRIDGE_GAP
    }

    return LinearFeatures.road(
      id = id,
      centerline = centerline,
      stationSpacing = params.roadSpacing,
      surfaceElevation = { s -> sampleElevation(elevation, centerline.pointAt(s)) },
      halfWidth = { s -> if (inGap(s)) 0.0 else tier.halfWidth },
      shoulder = { s -> if (inGap(s)) 0.0 else tier.shoulder },
      endTaper = tier.shoulder * 2.0
    )
  }

  private fun bridgeMarker(
    id: FeatureId,
    road: Polyline,
    crossing: Crossing,
    tier: RoadTier,
    elevation: Grid
  ): PointMarker {
    val bearing = road.tangentAt(crossing.roadArcLength)
    val deck = sampleElevation(elevation, crossing.point) + BRIDGE_CLEARANCE

    return PointMarker(
      id = id,
      kind = FeatureKind.BRIDGE,
      position = crossing.point,
      attributes = StationTable.Builder(1)
        .channel(BridgeChannels.DECK_ELEVATION) { deck }
        .channel(BridgeChannels.SPAN) { crossing.channelWidth + BRIDGE_GAP * 2.0 }
        .channel(BridgeChannels.HALF_WIDTH) { tier.halfWidth }
        // Stored rather than recomputed, so that a chunk laying the deck needs only the marker.
        .channel(BridgeChannels.BEARING_X) { bearing.x }
        .channel(BridgeChannels.BEARING_Y) { bearing.y }
        .build()
    )
  }

  // --- Helpers -------------------------------------------------------------------------------------

  /**
   * Ground elevation at a world position, clamped to the grid.
   *
   * Nearest cell rather than interpolated, deliberately: this feeds a road's *station* elevations, which are
   * then interpolated by the station spline anyway. Interpolating twice would smooth the profile past the
   * point where the road still follows the ground it is supposed to be on.
   */
  private fun sampleElevation(elevation: Grid, point: Vec2d): Double {
    val metres = resolution.metresPerCell
    val x = (point.x / metres).toInt().coerceIn(0, elevation.width - 1)
    val y = (point.y / metres).toInt().coerceIn(0, elevation.height - 1)
    return elevation.data[y * elevation.width + x]
  }

  private fun cellOf(region: CellRegion, position: Vec2d, metres: Double): Int? {
    val x = (position.x / metres).toInt() - region.minX
    val y = (position.y / metres).toInt() - region.minY
    if (x < 0 || y < 0 || x >= region.width || y >= region.height) return null
    return y * region.width + x
  }

  private fun centreOf(region: CellRegion, cell: Int, metres: Double) = Vec2d(
    (region.minX + cell % region.width + 0.5) * metres,
    (region.minY + cell / region.width + 0.5) * metres
  )

  /** Adds [value] to every cell within [radius] of a world position, keeping the maximum. */
  private fun stamp(grid: Grid, region: CellRegion, worldX: Double, worldY: Double, radius: Double, value: Double) {
    val metres = region.resolution.metresPerCell
    val cells = (radius / metres).toInt() + 1
    val cx = (worldX / metres).toInt() - region.minX
    val cy = (worldY / metres).toInt() - region.minY

    for (y in max(0, cy - cells)..min(region.height - 1, cy + cells)) {
      for (x in max(0, cx - cells)..min(region.width - 1, cx + cells)) {
        val dx = (x - cx) * metres
        val dy = (y - cy) * metres
        val distance = sqrt(dx * dx + dy * dy)
        if (distance > radius) continue

        val i = y * region.width + x
        grid.data[i] = max(grid.data[i], value * (1.0 - distance / radius))
      }
    }
  }

  companion object {
    val ID = StageId("settlements")

    private const val POPULATION_STREAM = 1L

    private const val ROAD_SMOOTHING = 2

    /** Metres of clear road either side of a channel before the deck begins. The abutments. */
    private const val BRIDGE_GAP = 4.0

    /** Metres the deck sits above the local ground, so there is room for water to pass under it. */
    private const val BRIDGE_CLEARANCE = 3.0

    private const val CONFLUENCE_RANGE = 4_000.0
    private const val MOUTH_RANGE = 6_000.0
    private const val MOUTH_DISCHARGE = 30.0
    private const val BIOME_EDGE_BONUS = 0.35

    /** Relief across a saddle at which it counts as a fully fledged pass, in metres. */
    private const val PASS_RELIEF = 320.0

    private const val ROAD_TRAFFIC = 4.0
    private const val HIGHWAY_TRAFFIC = 30.0

    /** East, north, north-east, north-west: two axes and two diagonals. */
    private val PASS_DX = intArrayOf(1, 0, 1, 1)
    private val PASS_DY = intArrayOf(0, 1, 1, -1)
  }
}

/**
 * A bucket grid for minimum-separation tests.
 *
 * Placement scans a quarter of a million candidate cells and rejects most of them for being too close to
 * something already placed. Testing each against every placed settlement is quadratic in a loop that is
 * already the longest in the stage; bucketing at the separation distance makes it a nine-bucket look-up.
 */
private class SeparationIndex(private val bucketMetres: Double) {

  private val buckets = HashMap<Long, ArrayList<Vec2d>>()

  fun add(position: Vec2d) {
    buckets.getOrPut(keyOf(position.x, position.y)) { ArrayList() }.add(position)
  }

  fun isClearOf(position: Vec2d, minDistance: Double): Boolean {
    val minSq = minDistance * minDistance
    val bx = Math.floor(position.x / bucketMetres).toLong()
    val by = Math.floor(position.y / bucketMetres).toLong()

    for (dy in -1..1) {
      for (dx in -1..1) {
        val bucket = buckets[key(bx + dx, by + dy)] ?: continue
        for (other in bucket) {
          if (other.distanceSquaredTo(position) < minSq) return false
        }
      }
    }

    return true
  }

  private fun keyOf(x: Double, y: Double) =
    key(Math.floor(x / bucketMetres).toLong(), Math.floor(y / bucketMetres).toLong())

  private fun key(bx: Long, by: Long) = (bx shl 32) or (by and 0xFFFFFFFFL)
}

/** Station channel names on a [FeatureKind.BRIDGE] marker. */
object BridgeChannels {
  const val DECK_ELEVATION = "deck_elevation"

  /** Length of the deck along the road, in metres. */
  const val SPAN = "span"

  /** Half the carriageway width, in metres. */
  const val HALF_WIDTH = "half_width"

  /** Unit direction of the road at the crossing. */
  const val BEARING_X = "bearing_x"
  const val BEARING_Y = "bearing_y"
}
