package net.bestia.zone.travel

import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdge
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.worldgen.core.NavGraph
import net.bestia.worldgen.core.NavNode
import net.bestia.worldgen.core.NavNodeId
import net.bestia.worldgen.core.NavNodeKind
import net.bestia.worldgen.vector.Aabb
import net.bestia.worldgen.vector.Vec2d
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.TestNavigation
import net.bestia.zone.navigation.graph.NavWorldSource
import net.bestia.zone.navigation.region.RegionGrid
import net.bestia.zone.navigation.region.WaySearches
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/** Which way [RoutePlanner] takes when the graph's course and the way straight over the ground disagree. */
class RoutePlannerTest {

  private val config = TravelConfig()
  private val knowledge = RouteKnowledge(config)
  private val master = Knower.Player(1L)

  @Test
  fun `a short walk between two far nodes is not led to them and back`() {
    // Trestein as it was met: the traveller's nearest node is one bridgehead and the click's the other, both
    // six hundred metres north. The course walked a kilometre up into town and back for 270 m.
    val town = Vec3L(0, 600, 0)
    val bridgehead = Vec3L(90, 600, 0)
    val planner = planner(Country(listOf(town, bridgehead), listOf(Link(0, 1, NavEdgeKind.BRIDGE))), DRY)

    val stops = plan(planner, from = Vec3L(265, 0, 0), to = Vec3L(30, 130, 0))

    assertTrue(stops.none { it.y > 300 }, "it went north towards the town: $stops")
  }

  @Test
  fun `a walk along a valley is not led over the hills to the graph's nodes`() {
    // Grafdelve to Stanhold as it was met: 8.3 km down a river valley, and the nodes nearest either end kilometres
    // off to the east, joined over the mountains. The course walked 30 km.
    val country = Country(
      listOf(Vec3L(5_000, 9_000, 0), Vec3L(6_000, 4_000, 0), Vec3L(5_000, -1_000, 0)),
      listOf(Link(0, 1, NavEdgeKind.WILDERNESS), Link(1, 2, NavEdgeKind.WILDERNESS)),
    )
    val valley = water(groundAt = { x, _ -> Math.abs(x) * 0.2 }) { _, _ -> false }
    val from = Vec3L(0, 8_300, 0)
    val to = Vec3L(300, 0, 0)

    val stops = plan(planner(country, valley), from, to)

    val length = (listOf(from) + stops + to).zipWithNext { a, b -> distance(a, b) }.sum()
    assertTrue(length < distance(from, to) * 1.1, "it walked $length m for ${distance(from, to)}: $stops")
    assertTrue(stops.all { Math.abs(it.x) < 500 }, "it left the valley: $stops")
  }

  @Test
  fun `a way beyond one search is laid a stretch at a time along the graph's course`() {
    val nodes = (0..6).map { Vec3L(it * 10_000L, 0, 0) }
    val country = Country(nodes, (0 until 6).map { Link(it, it + 1, NavEdgeKind.ROAD, costPerMetre = 0.5) })
    val journey = Journey(listOf(Vec3L(60_000, 0, 0)), JourneyOrder.PLAYER)

    val plan = planOf(planner(country, DRY), Vec3L(0, 0, 0), journey)

    assertTrue(plan.isStretch, "it was not laid a stretch at a time")
    assertEquals(Vec3L(10_000, 0, 0), plan.end, "the stretch did not end at the furthest node within reach")
    assertEquals(listOf(20_000L, 30_000L, 40_000L, 50_000L, 60_000L), plan.beyond.map { it.x })
  }

  @Test
  fun `the next stretch is laid from where this one ends, before the traveller gets there`() {
    val nodes = (0..6).map { Vec3L(it * 10_000L, 0, 0) }
    val planner = planner(Country(nodes, (0 until 6).map { Link(it, it + 1, NavEdgeKind.ROAD, costPerMetre = 0.5) }), DRY)
    val journey = Journey(listOf(Vec3L(60_000, 0, 0)), JourneyOrder.PLAYER)
    journey.plan = planOf(planner, Vec3L(0, 0, 0), journey)

    assertTrue(!planner.needsPlan(journey, master, Vec3L(5_000, 0, 0)), "it planned again half way")
    val nearTheEnd = Vec3L(9_000, 0, 0)
    assertTrue(planner.needsPlan(journey, master, nearTheEnd), "it waited for the end of the stretch")

    // Planned from somewhere else on purpose: the next stretch starts where this one ends, not where it stands.
    val next = planOf(planner, Vec3L(9_500, 300, 0), journey)

    assertEquals(Vec3L(10_000, 0, 0), next.remaining.first().position, "the stretch being walked was dropped")
    assertEquals(Vec3L(20_000, 0, 0), next.end)
  }

  @Test
  fun `a destination beside the town on the near side is walked to straight`() {
    val town = Vec3L(0, 0, 0)
    val planner = planner(Country(listOf(town, Vec3L(1000, 0, 0)), listOf(Link(0, 1, NavEdgeKind.ROAD))), DRY)

    val stops = plan(planner, from = Vec3L(520, 0, 0), to = Vec3L(300, -350, 0))

    assertTrue(town !in stops, "it went into the town and back out: $stops")
  }

  @Test
  fun `the way over the ground does not cross a bridge its master knows is down`() {
    // The grid takes the deck for ground, so only what the master knows keeps the way off it.
    val planner = planner(RIVER_COUNTRY, RIVER)
    knowledge.observe(master, RIVER_BRIDGE_EDGE, blocked = true)

    val stops = plan(planner, from = Vec3L(0, 0, 0), to = Vec3L(320, 0, 0))

    assertTrue(stops.any { it.y >= RIVER_SOURCE }, "it did not go round where the river rises: $stops")
  }

  @Test
  fun `a stop past a bridge blames the bridge`() {
    val planner = planner(RIVER_COUNTRY, RIVER)
    val journey = Journey(listOf(Vec3L(320, 0, 0)), JourneyOrder.PLAYER)

    val plan = planOf(planner, Vec3L(0, 0, 0), journey)

    assertTrue(plan.remaining.any { it.edge == RIVER_BRIDGE_EDGE }, "nothing blames the bridge: ${plan.remaining}")
  }

  @Test
  fun `a standing bridge is crossed`() {
    val planner = planner(RIVER_COUNTRY, RIVER)

    val stops = plan(planner, from = Vec3L(0, 0, 0), to = Vec3L(320, 0, 0))

    assertTrue(stops.none { it.y > 100 }, "it went round by the ford: $stops")
  }


  private fun planner(country: Country, water: WaterSource): RoutePlanner {
    val navigation = TestNavigation.service(worldSource = country.source)
    return RoutePlanner(navigation, knowledge, NavigationConfig(), WaySearches(RegionGrid(water), pool = null))
  }

  /** The stops a plan walks by. */
  private fun plan(planner: RoutePlanner, from: Vec3L, to: Vec3L): List<Vec3L> {
    return planOf(planner, from, Journey(listOf(to), JourneyOrder.PLAYER)).remaining.map { it.position }
  }

  /** The grid is searched on the caller here, so a plan is ready at once. */
  private fun planOf(planner: RoutePlanner, from: Vec3L, journey: Journey): TravelPlan {
    return when (val planned = planner.plan(from, journey, master)) {
      is RoutePlanner.Planned.Ready -> planned.plan
      RoutePlanner.Planned.NoRoute -> fail("No route from $from to ${journey.target}")
      RoutePlanner.Planned.NotYet -> fail("The grid was searched off the caller")
    }
  }

  private fun distance(a: Vec3L, b: Vec3L) = Math.hypot((b.x - a.x).toDouble(), (b.y - a.y).toDouble())

  private class Link(val a: Int, val b: Int, val kind: NavEdgeKind, val costPerMetre: Double = 1.0)

  /** A macro graph over [nodes], one position unit a metre. */
  private class Country(private val nodes: List<Vec3L>, private val links: List<Link>) {

    val source = object : NavWorldSource {
      override val isReady get() = true
      override fun navGraph() = graph()
      override fun place(metresX: Double, metresY: Double) = Vec3L(metresX.toLong(), metresY.toLong(), 0L)
      override fun chunkAt(position: Vec3L): ChunkPos? = null
      override fun onChunkChanged(handler: (ChunkPos) -> Unit) = Unit
    }

    private fun graph(): NavGraph {
      val navNodes = nodes.mapIndexed { i, at ->
        NavNode(NavNodeId(i), Vec2d(at.x.toDouble(), at.y.toDouble()), NavNodeKind.WILDERNESS)
      }
      val edges = links.map { link ->
        val from = nodes[link.a]
        val to = nodes[link.b]
        val length = Math.hypot((to.x - from.x).toDouble(), (to.y - from.y).toDouble())
        val cost = length * link.costPerMetre
        NavEdge(NavNodeId(link.a), NavNodeId(link.b), link.kind, length, cost, setOf(MovementMode.WALK))
      }

      return NavGraph(navNodes, edges)
    }
  }

  private companion object {
    val DRY = water { _, _ -> false }

    /**
     * A river from the far south to y 400, bridged on the x axis, and a ford well north of where it rises:
     *
     * ```
     *   WEST ── BRIDGE_WEST ══bridge══ BRIDGE_EAST ── EAST
     *     └────────────── FORD (far north) ──────────────┘
     * ```
     */
    const val RIVER_SOURCE = 400L

    val RIVER = water(crossings = listOf(Vec2d(96.0, 0.0) to Vec2d(224.0, 0.0))) { x, y ->
      x in 100.0..220.0 && y < RIVER_SOURCE
    }

    val RIVER_FORD = Vec3L(160, 900, 0)

    val RIVER_COUNTRY = Country(
      listOf(Vec3L(0, 0, 0), Vec3L(96, 0, 0), Vec3L(224, 0, 0), Vec3L(320, 0, 0), RIVER_FORD),
      listOf(
        Link(0, 1, NavEdgeKind.ROAD),
        Link(1, 2, NavEdgeKind.BRIDGE),
        Link(2, 3, NavEdgeKind.ROAD),
        Link(0, 4, NavEdgeKind.WILDERNESS),
        Link(4, 3, NavEdgeKind.WILDERNESS),
      ),
    )

    const val RIVER_BRIDGE_EDGE = 1

    fun water(
      crossings: List<Pair<Vec2d, Vec2d>> = emptyList(),
      groundAt: (Double, Double) -> Double = { _, _ -> 0.0 },
      isDeep: (Double, Double) -> Boolean,
    ) = object : WaterSource {
      override val isReady get() = true
      override fun survey(area: Aabb, exact: Boolean) = WaterSurvey(isDeep, crossings, groundAt)
    }
  }
}
