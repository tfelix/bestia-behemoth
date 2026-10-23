package net.bestia.zone.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.AStar
import net.bestia.zone.navigation.graph.MacroGraphService
import net.bestia.zone.navigation.graph.MacroPathfinder
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
   * Empty when there is nothing to steer by - no graph, or both ends nearest the same node - so the traveller
   * walks straight. Which edges count as blocked is the caller's: a traveller avoids what it believes, not
   * what is true.
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

    metrics.macro(NavigationMetrics.Macro.PLANNED)
    return MacroCourse(nodes.drop(1), edges)
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
