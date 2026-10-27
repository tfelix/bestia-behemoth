package net.bestia.zone.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.MovementMode
import net.bestia.worldgen.core.NavEdgeKind
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.AStar
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
   * Empty when there is nothing to steer by - no graph, both ends nearest the same node, or a goal node that
   * stands past the destination (see [overshoots]) - so the traveller walks straight. Which edges count as
   * blocked is the caller's: a traveller avoids what it believes, not what is true.
   */
  fun planCourse(from: Vec3L, destination: Vec3L, profileId: String?, isBlocked: (Int) -> Boolean): MacroCourse? {
    macroGraph.ensureLoaded()
    val graph = macroGraph.graph
    if (graph.isEmpty) return ended(NavigationMetrics.Macro.NO_GRAPH, MacroCourse(emptyList(), emptyList()))

    val start = graph.nearestNode(from) ?: return ended(NavigationMetrics.Macro.NO_START)
    val goal = graph.nearestNode(destination) ?: return ended(NavigationMetrics.Macro.NO_GOAL)
    if (start == goal) return ended(NavigationMetrics.Macro.SAME_NODE, MacroCourse(emptyList(), emptyList()))

    val pathfinder = MacroPathfinder(graph, profiles.getOrDefault(profileId), isBlocked)
    val nodes = AStar.findPath(pathfinder, start, goal) ?: return ended(NavigationMetrics.Macro.NO_ROUTE)
    val edges = nodes.zipWithNext { a, b ->
      pathfinder.cheapestEdge(a, b) ?: return ended(NavigationMetrics.Macro.NO_ROUTE)
    }

    if (overshoots(graph, from, destination, goal, edges)) {
      return ended(NavigationMetrics.Macro.OVERSHOT, MacroCourse(emptyList(), emptyList()))
    }

    metrics.macro(NavigationMetrics.Macro.PLANNED)
    return MacroCourse(nodes.drop(1), edges)
  }

  /**
   * Whether the traveller already stands as near the destination as the course's [goal] does.
   *
   * The goal is only the node nearest the destination, and with nodes kilometres apart it can stand well past
   * it: a click beside a town, on the far side from its centre, walked to the centre and then back out. The
   * last stretch from the goal is walked straight anyway, so walking straight from here is no longer.
   *
   * Not when the course crosses water. Its bridge, sea lane or ford is what gets the traveller over, and the
   * traveller may well be nearer the destination as the crow flies from the other bank.
   */
  private fun overshoots(graph: RuntimeNavGraph, from: Vec3L, destination: Vec3L, goal: Int, edges: List<Int>): Boolean {
    val crossesWater = edges.any { index ->
      val edge = graph.edges[index]
      edge.kind == NavEdgeKind.BRIDGE || edge.kind == NavEdgeKind.SEA_LANE || MovementMode.SWIM in edge.modes
    }
    if (crossesWater) return false

    return flatDistanceSq(from, destination) <= flatDistanceSq(graph.nodePositions[goal], destination)
  }

  /** Node positions carry no height, so only the horizontal counts. */
  private fun flatDistanceSq(a: Vec3L, b: Vec3L): Long {
    val dx = a.x - b.x
    val dy = a.y - b.y
    return dx * dx + dy * dy
  }

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

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
