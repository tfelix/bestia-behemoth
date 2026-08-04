package net.bestia.zone.navigation

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.AStar
import net.bestia.zone.navigation.graph.MacroGraphService
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
  private val config: NavigationConfig
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

  /**
   * A macro route from [from] to [destination], or null when the two are not connected for this creature.
   *
   * The returned route holds node ids, not positions: it is a plan, and turning each leg of it into ground is
   * [refineLeg]'s job as the traveller arrives.
   */
  fun planRoute(from: Vec3L, destination: Vec3L, profileId: String?): MacroRoute? {
    macroGraph.ensureLoaded()
    val graph = macroGraph.graph
    if (graph.isEmpty) return null

    val start = graph.nearestNode(from) ?: return null
    val goal = graph.nearestNode(destination) ?: return null
    if (start == goal) return null

    val profile = profiles.getOrDefault(profileId)
    val nodes = AStar.findPath(macroGraph.pathfinderFor(profile), start, goal) ?: return null

    // The first node is the one nearest where the entity already stands, so walking to it would often mean
    // walking backwards to a road junction it has already passed.
    val ahead = nodes.drop(1)
    if (ahead.isEmpty()) return null

    return MacroRoute(
      remaining = ahead.toMutableList(),
      destination = destination,
      graphVersion = macroGraph.version
    )
  }

  /**
   * Turns the next leg of [route] into walkable columns, advancing it when the current node is reached.
   *
   * The macro graph's waypoints are hundreds of metres apart, so they are not a path - they are a corridor to
   * follow. Each call refines only as far as the next waypoint, through the local tier, so what an NPC walks is
   * always ground that was checked against the real voxels.
   */
  fun refineLeg(route: MacroRoute, from: Vec3L): List<Vec3L>? {
    val graph = macroGraph.graph
    val node = route.currentNode() ?: return null
    val target = graph.nodePositions.getOrNull(node) ?: return null

    // Arrived at this node: drop it and aim at the next one, so a leg is never re-walked.
    if (isNear(from, target, ARRIVAL_RADIUS)) {
      route.advance()
      val next = route.currentNode() ?: return null
      return refineTowards(from, graph.nodePositions[next])
    }

    return refineTowards(from, target)
  }

  /**
   * Whether an NPC should replan, given that the world has changed since it planned.
   *
   * Staleness alone is not enough, deliberately. Every traveller in the world would notice the same version
   * bump on the same tick, and replanning them all together is both a spike and a tell - a hundred NPCs
   * simultaneously changing their minds reads as a system reacting, not as creatures finding out. So the first
   * time a route is seen to be stale, a moment is picked for it inside the configured jitter and it carries on
   * until then.
   */
  fun shouldReplan(route: MacroRoute, currentTick: Long, tickRate: Int): Boolean {
    if (route.graphVersion == macroGraph.version) return false

    if (route.replanAfterTick == 0L) {
      val window = (config.macroReplanJitterSeconds * tickRate).toLong().coerceAtLeast(1L)
      route.replanAfterTick = currentTick + (0 until window).random()
      return false
    }

    return currentTick >= route.replanAfterTick
  }

  fun profileFor(profileId: String?): MovementProfile = profiles.getOrDefault(profileId)

  private fun refineTowards(from: Vec3L, target: Vec3L): List<Vec3L>? {
    // Within local range: hand the whole remainder to the accurate tier.
    if (isNear(from, target, config.localSearchSpan)) {
      return local.path(from, target) ?: local.step(from, target)?.let { listOf(it) }
    }

    // Further off: walk towards it along the straight line, one local search at a time. The corridor the
    // macro edge describes is what keeps this from being blind - it was routed over the cost field, so
    // following it does not walk into the lake the edge went round.
    val dx = target.x - from.x
    val dy = target.y - from.y
    val distance = Math.max(Math.abs(dx), Math.abs(dy)).toDouble()
    val stride = config.localSearchSpan.toDouble() / distance

    val waypoint = Vec3L(
      from.x + Math.round(dx * stride),
      from.y + Math.round(dy * stride),
      from.z
    )

    return local.path(from, waypoint) ?: local.step(from, waypoint)?.let { listOf(it) }
  }

  private fun isNear(a: Vec3L, b: Vec3L, radius: Long): Boolean =
    Math.abs(a.x - b.x) <= radius && Math.abs(a.y - b.y) <= radius

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /**
     * How close counts as having reached a macro node, in position units.
     *
     * A node is a *place*, not a spot - a settlement node is the middle of a town - so insisting on the exact
     * column would leave a traveller circling a doorway it is already standing in.
     */
    private const val ARRIVAL_RADIUS = 8L
  }
}
