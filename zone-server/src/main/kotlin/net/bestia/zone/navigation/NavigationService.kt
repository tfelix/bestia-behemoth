package net.bestia.zone.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.AStar
import net.bestia.zone.navigation.graph.AStarGraph
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.graph.MacroPathfinder
import net.bestia.zone.navigation.graph.RuntimeNavGraph
import net.bestia.zone.navigation.local.LocalPathfindingService
import net.bestia.zone.navigation.profile.MovementProfile
import net.bestia.zone.navigation.profile.MovementProfileRegistry
import org.springframework.stereotype.Service

/**
 * Where NPCs ask how to get somewhere.
 *
 * Two tiers, and which one answers depends on how far away the destination is:
 *
 * - **Local** - anything within sight. Real columns, real voxels, accurate to the tile, and it does not
 *   involve the macro graph at all. Wandering, closing on a target and backing off all live here.
 * - **Macro** - anything further. A route over the world's node graph, filtered and weighted for the
 *   creature, whose legs are refined into columns one at a time as the traveller reaches them.
 *
 * The split is not a performance trick, it is what makes both parts possible: a tile-accurate search across a
 * continent would expand millions of columns, and a graph whose nodes are kilometres apart cannot tell an NPC
 * which side of a boulder to walk past.
 */
@Service
class NavigationService(
  private val macroGraph: MacroGraphService,
  private val local: LocalPathfindingService,
  private val profiles: MovementProfileRegistry,
  private val config: NavigationConfig,
  private val metrics: NavigationMetrics = NavigationMetrics(),
) {

  /**
   * A path to [to] for the entity at [from], using whichever tier fits the distance.
   *
   * Returns the waypoints still to walk, or null when there is no route - or when the tick's local budget is
   * spent, which a caller does not need to distinguish: both mean "carry on and ask again".
   */
  fun pathTo(from: Vec3L, to: Vec3L, profileId: String? = null): List<Vec3L>? =
    local.path(from, to)

  /** One walkable step towards [to]. The fallback when a considered path is unavailable this tick. */
  fun stepToward(from: Vec3L, to: Vec3L): Vec3L? = local.step(from, to)

  /** [pathTo] for a caller that budgets its own searches; see [LocalPathfindingService.pathOutsideBudget]. */
  fun pathOutsideBudget(from: Vec3L, to: Vec3L): List<Vec3L>? {
    return local.pathOutsideBudget(from, to)
  }

  /**
   * A course from [from] to [destination] that avoids every edge [isBlocked] names, or null when there is none
   * for this creature.
   *
   * The graph is entered at whichever of the [entries] nodes nearest [from] makes the cheapest course, and left
   * at whichever of those nearest [destination] does, each reached straight over open country. Over a long way
   * the nearest node alone can stand behind the traveller, or up a mountain beside it; over a short one, a
   * straight line to a further node may cross the very river the graph would have bridged, so take one.
   *
   * Empty when there is nothing to steer by - no graph, or a course through one node only - so the traveller
   * walks straight. Which edges count as blocked is the caller's: a traveller avoids what it believes, not what
   * is true.
   */
  fun planCourse(
    from: Vec3L,
    destination: Vec3L,
    profileId: String?,
    entries: Int = 1,
    isBlocked: (Int) -> Boolean,
  ): MacroCourse? {
    macroGraph.ensureLoaded()
    val graph = macroGraph.graph
    if (graph.isEmpty) return ended(NavigationMetrics.Macro.NO_GRAPH, MacroCourse(emptyList(), emptyList()))

    val starts = graph.nearestNodes(from, entries)
    val goals = graph.nearestNodes(destination, entries)
    if (starts.isEmpty()) return ended(NavigationMetrics.Macro.NO_START)
    if (goals.isEmpty()) return ended(NavigationMetrics.Macro.NO_GOAL)

    val pathfinder = MacroPathfinder(graph, profiles.getOrDefault(profileId), isBlocked)
    val entered = EnteredGraph(pathfinder, graph.nodePositions, from, destination, starts, goals.toSet())
    val path = AStar.findPath(entered, EnteredGraph.FROM, EnteredGraph.DESTINATION)
      ?: return ended(NavigationMetrics.Macro.NO_ROUTE)
    val nodes = path.drop(1).dropLast(1)
    if (nodes.size < 2) return ended(NavigationMetrics.Macro.SAME_NODE, MacroCourse(emptyList(), emptyList()))

    val edges = nodes.zipWithNext { a, b ->
      pathfinder.cheapestEdge(a, b) ?: return ended(NavigationMetrics.Macro.NO_ROUTE)
    }

    metrics.macro(NavigationMetrics.Macro.PLANNED)
    return MacroCourse(nodes.drop(1), edges)
  }

  /** The stretches of every bridge whose edge [isBlocked] names, for a way over the water grid to keep clear of. */
  fun blockedBridgeSpans(isBlocked: (Int) -> Boolean): List<Pair<Vec3L, Vec3L>> {
    return bridges().filter { isBlocked(it.edge) }.flatMap { it.stretches }
  }

  /**
   * The bridge a stretch from [a] to [b] crosses, if any: what a traveller stuck on a way over the water grid
   * blames, since the grid takes every deck for ground whether it still stands or not.
   */
  fun bridgeCrossedBy(a: Vec3L, b: Vec3L): Int? {
    return bridges().firstOrNull { bridge ->
      bridge.stretches.any { (c, d) -> segmentDistance(a, b, c, d) <= BRIDGE_REACH }
    }?.edge
  }

  /** A bridge edge and the stretches its deck runs along. */
  private class Bridge(val edge: Int, val stretches: List<Pair<Vec3L, Vec3L>>)

  private fun bridges(): List<Bridge> {
    macroGraph.ensureLoaded()
    val graph = macroGraph.graph
    if (graph !== bridgesOf) {
      bridges = graph.edges.withIndex().filter { it.value.kind == NavEdgeKind.BRIDGE }.map { (index, edge) ->
        val span = listOf(graph.nodePositions[edge.a]) + edge.waypoints + graph.nodePositions[edge.b]
        Bridge(index, span.zipWithNext())
      }
      bridgesOf = graph
    }
    return bridges
  }

  private var bridges: List<Bridge> = emptyList()
  private var bridgesOf: RuntimeNavGraph? = null

  fun nodePosition(node: Int): Vec3L {
    return macroGraph.graph.nodePositions[node]
  }

  /** The bends of [edge] in the order a traveller meets them walking to [node], one of its ends. */
  fun bendsTowards(edge: Int, node: Int): List<Vec3L> {
    val runtimeEdge = macroGraph.graph.edges[edge]
    return if (runtimeEdge.b == node) runtimeEdge.waypoints else runtimeEdge.waypoints.asReversed()
  }

  private fun ended(outcome: NavigationMetrics.Macro, course: MacroCourse? = null): MacroCourse? {
    metrics.macro(outcome)

    return course
  }

  fun profileFor(profileId: String?): MovementProfile = profiles.getOrDefault(profileId)

  /**
   * The macro graph with the traveller's two ends added as nodes of their own: [FROM] joined to each of
   * [starts], and each of [goals] joined to [DESTINATION], at what crossing open country to them costs.
   */
  private class EnteredGraph(
    private val graph: MacroPathfinder,
    private val positions: Array<Vec3L>,
    private val from: Vec3L,
    private val destination: Vec3L,
    private val starts: List<Int>,
    private val goals: Set<Int>,
  ) : AStarGraph<Int> {

    override fun neighbors(node: Int): List<Int> {
      return when (node) {
        FROM -> starts
        DESTINATION -> emptyList()
        else -> if (node in goals) graph.neighbors(node) + DESTINATION else graph.neighbors(node)
      }
    }

    override fun cost(from: Int, to: Int): Double {
      return when {
        from == FROM -> graph.offGraphCost(this.from, positions[to])
        to == DESTINATION -> graph.offGraphCost(positions[from], destination)
        else -> graph.cost(from, to)
      }
    }

    override fun heuristic(node: Int, goal: Int): Double {
      return when (node) {
        FROM -> graph.estimate(from, destination)
        DESTINATION -> 0.0
        else -> graph.estimate(positions[node], destination)
      }
    }

    companion object {
      const val FROM = -1
      const val DESTINATION = -2
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** A grid way crosses a river only through the cells a deck clears, whose middles lie within 23 m of it. */
    const val BRIDGE_REACH = 32.0

    /** The horizontal distance between segments [a]-[b] and [c]-[d]: zero where they cross. */
    fun segmentDistance(a: Vec3L, b: Vec3L, c: Vec3L, d: Vec3L): Double {
      if (side(a, b, c) * side(a, b, d) < 0 && side(c, d, a) * side(c, d, b) < 0) return 0.0

      return minOf(pointDistance(a, c, d), pointDistance(b, c, d), pointDistance(c, a, b), pointDistance(d, a, b))
    }

    /** Which side of the line through [a] and [b] the point [p] lies on, by the sign. */
    private fun side(a: Vec3L, b: Vec3L, p: Vec3L): Long {
      return java.lang.Long.signum((b.x - a.x) * (p.y - a.y) - (b.y - a.y) * (p.x - a.x)).toLong()
    }

    private fun pointDistance(p: Vec3L, a: Vec3L, b: Vec3L): Double {
      val dx = (b.x - a.x).toDouble()
      val dy = (b.y - a.y).toDouble()
      val lengthSq = dx * dx + dy * dy
      val t = if (lengthSq == 0.0) 0.0 else (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSq).coerceIn(0.0, 1.0)

      return Math.hypot(p.x - (a.x + dx * t), p.y - (a.y + dy * t))
    }
  }
}
