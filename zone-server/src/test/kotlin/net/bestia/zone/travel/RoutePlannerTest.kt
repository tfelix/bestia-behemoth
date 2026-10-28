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
import net.bestia.zone.navigation.region.WaterSource
import net.bestia.zone.navigation.region.WaterSurvey
import net.bestia.zone.travel.knowledge.Knower
import net.bestia.zone.travel.knowledge.RouteKnowledge
import kotlin.test.Test
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
  fun `a destination past the town goes by the town`() {
    val town = Vec3L(0, 0, 0)
    val planner = planner(Country(listOf(town, Vec3L(1000, 0, 0)), listOf(Link(0, 1, NavEdgeKind.ROAD))), DRY)

    val stops = plan(planner, from = Vec3L(520, 0, 0), to = Vec3L(-100, 0, 0))

    assertTrue(town in stops, "it did not go by the town: $stops")
  }

  @Test
  fun `a destination beside the town on the near side is walked to straight`() {
    val town = Vec3L(0, 0, 0)
    val planner = planner(Country(listOf(town, Vec3L(1000, 0, 0)), listOf(Link(0, 1, NavEdgeKind.ROAD))), DRY)

    val stops = plan(planner, from = Vec3L(520, 0, 0), to = Vec3L(300, -350, 0))

    assertTrue(town !in stops, "it went into the town and back out: $stops")
  }

  @Test
  fun `the way straight over the ground does not cross a bridge its master knows is down`() {
    // The grid takes the deck for ground, so the straight way crosses by it and is far shorter than the ford.
    val planner = planner(RIVER_COUNTRY, RIVER)
    knowledge.observe(master, RIVER_BRIDGE_EDGE, blocked = true)

    val stops = plan(planner, from = Vec3L(0, 0, 0), to = Vec3L(320, 0, 0))

    assertTrue(RIVER_FORD in stops, "it did not go round by the ford: $stops")
  }

  @Test
  fun `a standing bridge is crossed`() {
    val planner = planner(RIVER_COUNTRY, RIVER)

    val stops = plan(planner, from = Vec3L(0, 0, 0), to = Vec3L(320, 0, 0))

    assertTrue(stops.none { it.y > 100 }, "it went round by the ford: $stops")
  }

  private val budget = TravelBudget(config)

  private fun planner(country: Country, water: WaterSource): RoutePlanner {
    val navigation = TestNavigation.service(worldSource = country.source)
    return RoutePlanner(navigation, knowledge, NavigationConfig(), RegionGrid(water), budget)
  }

  /** The stops a plan walks by, surveying the grid for as many ticks as that takes. */
  private fun plan(planner: RoutePlanner, from: Vec3L, to: Vec3L): List<Vec3L> {
    val journey = Journey(listOf(to), JourneyOrder.PLAYER)

    repeat(1_000) {
      budget.refill()
      when (val planned = planner.plan(from, journey, master)) {
        is RoutePlanner.Planned.Ready -> return planned.plan.remaining.map { it.position }
        RoutePlanner.Planned.NoRoute -> fail("No route from $from to $to")
        RoutePlanner.Planned.NotYet -> Unit
      }
    }
    fail("The grid was still surveying")
  }

  private class Link(val a: Int, val b: Int, val kind: NavEdgeKind)

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
        NavEdge(NavNodeId(link.a), NavNodeId(link.b), link.kind, length, length, setOf(MovementMode.WALK))
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
    val RIVER = water(crossings = listOf(Vec2d(96.0, 0.0) to Vec2d(224.0, 0.0))) { x, y -> x in 100.0..220.0 && y < 400.0 }

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

    fun water(crossings: List<Pair<Vec2d, Vec2d>> = emptyList(), isDeep: (Double, Double) -> Boolean) =
      object : WaterSource {
        override val isReady get() = true
        override fun survey(area: Aabb, exact: Boolean) = WaterSurvey(isDeep, crossings)
      }
  }
}
