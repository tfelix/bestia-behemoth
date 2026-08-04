package net.bestia.zone.navigation.graph

import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.profile.MovementProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-species routing rules, which are the point of the whole two-enum design.
 *
 * Every case here is built on the same tiny graph: two towns, joined both by a road and by a longer
 * open-country track. That is the situation the requirements describe - a merchant should take the road and a
 * wild animal should avoid it - and it is exactly what a single unweighted graph could not express.
 */
class MacroPathfinderTest {

  /**
   * ```
   *   0 -------- road, 1000 m, baseCost 500 -------- 1
   *    \                                            /
   *     2 --- 400 --- 3 --- 400 ---     (400 each, 1200 total)
   * ```
   *
   * The costs are the generator's own scales: a made surface is 0.5 per metre and open ground about 1.0, so
   * the road is physically shorter *and* cheaper before any species weighting. Which route wins is then pure
   * arithmetic, and it is worth being able to check it by eye:
   *
   * | profile  | road          | track          | winner |
   * |----------|---------------|----------------|--------|
   * | merchant | 500 x 0.4 = 200  | 1200 x 2.0 = 2400 | road  |
   * | wolf     | 500 x 2.5 = 1250 | 1200 x 1.0 = 1200 | track |
   * | neutral  | 500 x 1.0 = 500  | 1200 x 1.0 = 1200 | road  |
   *
   * The wolf's margin is deliberately narrow - 1200 against 1250 - because that is the honest situation: a
   * road-avoiding creature takes the long way only when the detour is not *much* worse, which is what stops
   * it from crossing a continent to dodge a footpath.
   */
  private fun twoTownsGraph(): RuntimeNavGraph {
    val positions = arrayOf(
      Vec3L(0, 0, 0),
      Vec3L(1000, 0, 0),
      Vec3L(200, 400, 0),
      Vec3L(800, 400, 0)
    )
    val kinds = Array(4) { RuntimeNavGraph.NavNodeKindView.WILDERNESS }

    val edges = listOf(
      edge(0, 1, NavEdgeKind.ROAD, lengthMetres = 1000.0, baseCost = 500.0),
      edge(0, 2, NavEdgeKind.WILDERNESS, lengthMetres = 400.0, baseCost = 400.0),
      edge(2, 3, NavEdgeKind.WILDERNESS, lengthMetres = 400.0, baseCost = 400.0),
      edge(3, 1, NavEdgeKind.WILDERNESS, lengthMetres = 400.0, baseCost = 400.0)
    )

    return build(positions, kinds, edges)
  }

  private fun edge(
    a: Int,
    b: Int,
    kind: NavEdgeKind,
    lengthMetres: Double,
    baseCost: Double,
    modes: Set<MovementMode> = setOf(MovementMode.WALK),
    maxAgentHalfWidth: Double = Double.MAX_VALUE
  ) = RuntimeNavEdge(a, b, kind, modes, baseCost, lengthMetres, maxAgentHalfWidth, emptyList())

  private fun build(
    positions: Array<Vec3L>,
    kinds: Array<RuntimeNavGraph.NavNodeKindView>,
    edges: List<RuntimeNavEdge>
  ): RuntimeNavGraph {
    val incident = Array(positions.size) { ArrayList<Int>() }
    for ((index, edge) in edges.withIndex()) {
      incident[edge.a].add(index)
      incident[edge.b].add(index)
    }
    return RuntimeNavGraph(positions, kinds, edges, Array(positions.size) { incident[it].toIntArray() })
  }

  private fun profile(
    identifier: String,
    road: Double,
    offRoad: Double,
    canSwim: Boolean = false,
    halfWidth: Double = 0.5
  ): MovementProfile {
    val capabilities = mutableSetOf(MovementMode.WALK, MovementMode.CLIMB)
    if (canSwim) capabilities.add(MovementMode.SWIM)
    return MovementProfile(identifier, capabilities, halfWidth, road, offRoad)
  }

  private fun route(graph: RuntimeNavGraph, profile: MovementProfile, blocked: Set<Int> = emptySet()) =
    AStar.findPath(MacroPathfinder(graph, profile) { it in blocked }, 0, 1)

  @Test
  fun `a merchant takes the road and a wild animal takes the long way round`() {
    // The headline requirement, and both halves have to hold on the *same* graph - which is the claim that
    // one generated route set can serve every species.
    val graph = twoTownsGraph()

    val merchant = route(graph, profile("merchant", road = 0.4, offRoad = 2.0))
    val wolf = route(graph, profile("wolf", road = 2.5, offRoad = 1.0))

    assertEquals(listOf(0, 1), merchant, "a merchant should take the road")
    assertEquals(listOf(0, 2, 3, 1), wolf, "a wild animal should avoid the road even though it is shorter")
  }

  @Test
  fun `a creature with no opinion about roads takes the physically cheaper way`() {
    val neutral = route(twoTownsGraph(), profile("neutral", road = 1.0, offRoad = 1.0))

    // Road 500 against 1200 for the track: with no preference, cost alone decides.
    assertEquals(listOf(0, 1), neutral)
  }

  @Test
  fun `a cart is refused a crossing narrower than itself`() {
    // "Too big for this bridge", which is the one restriction that comes from geometry rather than capability.
    val positions = arrayOf(Vec3L(0, 0, 0), Vec3L(100, 0, 0))
    val narrow = listOf(edge(0, 1, NavEdgeKind.BRIDGE, 100.0, 50.0, maxAgentHalfWidth = 1.0))
    val graph = build(positions, Array(2) { RuntimeNavGraph.NavNodeKindView.BRIDGE_APPROACH }, narrow)

    val onFoot = route(graph, profile("walker", 1.0, 1.0, halfWidth = 0.5))
    val cart = route(graph, profile("cart", 1.0, 1.0, halfWidth = 1.5))

    assertEquals(listOf(0, 1), onFoot, "a person fits over a footbridge")
    assertNull(cart, "a cart wider than the deck must be refused it")
  }

  @Test
  fun `a creature that cannot swim is refused a ford and finds a dry way instead`() {
    // Modes are a conjunction: the ford is {WALK, SWIM}, so WALK alone must not satisfy it. Under an any-of
    // reading the non-swimmer would take the short wet route and drown in it.
    val positions = arrayOf(Vec3L(0, 0, 0), Vec3L(1000, 0, 0), Vec3L(500, 900, 0))
    val edges = listOf(
      edge(0, 1, NavEdgeKind.WILDERNESS, 1000.0, 100.0, modes = setOf(MovementMode.WALK, MovementMode.SWIM)),
      edge(0, 2, NavEdgeKind.WILDERNESS, 1000.0, 1000.0),
      edge(2, 1, NavEdgeKind.WILDERNESS, 1000.0, 1000.0)
    )
    val graph = build(positions, Array(3) { RuntimeNavGraph.NavNodeKindView.WILDERNESS }, edges)

    val swimmer = route(graph, profile("otter", 1.0, 1.0, canSwim = true))
    val landlocked = route(graph, profile("boar", 1.0, 1.0, canSwim = false))

    assertEquals(listOf(0, 1), swimmer, "a swimmer takes the cheap ford")
    assertEquals(listOf(0, 2, 1), landlocked, "a non-swimmer must go the long dry way round")
  }

  @Test
  fun `a blocked edge is not offered and the route goes round it`() {
    // What a destroyed bridge does. The graph itself never changes - only the overlay - so the alternative
    // route has to be found through the same structure.
    val graph = twoTownsGraph()
    val merchant = profile("merchant", road = 0.4, offRoad = 2.0)

    assertEquals(listOf(0, 1), route(graph, merchant), "with the road open the merchant uses it")

    // Edge 0 is the road. With it out, even a road-loving merchant must take the track.
    val detour = assertNotNull(route(graph, merchant, blocked = setOf(0)))
    assertEquals(listOf(0, 2, 3, 1), detour)
  }

  @Test
  fun `a route is impossible when every way is blocked`() {
    val graph = twoTownsGraph()
    val walker = profile("walker", 1.0, 1.0)

    assertNull(route(graph, walker, blocked = setOf(0, 1, 2, 3)))
  }

  @Test
  fun `nearestNode snaps a position onto the graph and respects its limit`() {
    val graph = twoTownsGraph()

    assertEquals(0, graph.nearestNode(Vec3L(10, 10, 0)))
    assertEquals(1, graph.nearestNode(Vec3L(990, 20, 0)))
    assertNull(graph.nearestNode(Vec3L(50_000, 50_000, 0), maxDistance = 100.0))
  }

  @Test
  fun `the heuristic never exceeds the true cost for a road-preferring creature`() {
    // Admissibility under per-species weighting, which is the subtle one: a merchant pays 0.4 x 0.5 per metre
    // on a road, so a heuristic scaled for an ordinary walker would over-estimate for exactly the creature the
    // road network exists to serve - and A* would then return a route that merely looks sensible.
    val graph = twoTownsGraph()
    val merchant = profile("merchant", road = 0.4, offRoad = 2.0)
    val finder = MacroPathfinder(graph, merchant) { false }

    val estimate = finder.heuristic(0, 1)
    val actual = finder.cost(0, 1)

    assertTrue(
      estimate <= actual + 1e-9,
      "heuristic $estimate over-estimates the real cost $actual, which breaks A*'s optimality"
    )
  }
}
