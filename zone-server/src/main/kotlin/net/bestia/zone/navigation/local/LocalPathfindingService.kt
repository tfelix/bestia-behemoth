package net.bestia.zone.navigation.local

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.NavigationConfig
import net.bestia.zone.navigation.graph.AStar
import org.springframework.stereotype.Service

/**
 * Short, accurate paths over the ground as it actually is.
 *
 * This is the tier that answers "walk over there" for everything an NPC does within sight of itself -
 * wandering, closing on a target, backing away from one - and it is also what turns each leg of a long
 * journey into real steps as the traveller reaches it.
 *
 * ### The budget is not optional
 *
 * Every search asks [LocalWalkQuery] about columns, which reads derived walkability tiles. A tile that is not
 * resident would have to be built from voxels - half a megabyte decoded - so [LocalWalkQuery.isResident]
 * makes the pathfinder treat absent ground as impassable rather than pay to find out. That bounds the cost of
 * one search; [NavigationConfig.localPathfindsPerTick] bounds how many searches a tick can contain, because
 * a pack of NPCs whose paths all run out on the same tick would otherwise each start one.
 *
 * A refused search is not a failure the caller has to handle specially: the entity keeps walking whatever
 * path it already had, and asks again next tick. Movement degrades to slightly stale rather than stopping.
 */
@Service
class LocalPathfindingService(private val walkQuery: LocalWalkQuery, private val config: NavigationConfig) {

  private var spentThisTick = 0

  /** Called once per tick by [net.bestia.zone.navigation.graph.MacroGraphMaintenanceSystem]. */
  fun resetBudget() {
    spentThisTick = 0
  }

  val budgetRemaining: Int get() = (config.localPathfindsPerTick - spentThisTick).coerceAtLeast(0)

  /**
   * A walkable path from [from] to [to], excluding the starting column, or null.
   *
   * Null covers three different situations on purpose, because no caller wants to tell them apart: there is
   * no route, the route is longer than this tier should be asked about, or the tick's budget is spent. All
   * three mean "keep doing what you were doing".
   */
  fun path(from: Vec3L, to: Vec3L): List<Vec3L>? {
    if (from.x == to.x && from.y == to.y) return null
    if (spentThisTick >= config.localPathfindsPerTick) return null

    // Refused before the budget is charged: a target on the far side of the world is a caller mistake or a
    // job for the macro tier, and either way expanding a few thousand columns to discover that is waste.
    val span = maxOf(Math.abs(to.x - from.x), Math.abs(to.y - from.y))
    if (span > config.localSearchSpan) return null

    spentThisTick++

    val start = walkQuery.surfaceAt(from)?.let { Vec3L(from.x, from.y, it) } ?: from
    val goal = walkQuery.surfaceAt(to)?.let { Vec3L(to.x, to.y, it) } ?: return null

    val graph = LocalWalkGraph(
      query = walkQuery,
      goal = goal,
      bounds = LocalWalkGraph.Bounds.around(start, goal, config.localSearchMargin)
    )

    val path = AStar.findPath(graph, start, goal, nodeLimit = config.localExpansionLimit)
      ?: return null

    // The first element is where the entity already is; `Path` is a queue of places still to go.
    return path.drop(1).takeIf { it.isNotEmpty() }
  }

  /**
   * One step towards [to], for a caller that wants movement now rather than a considered route.
   *
   * The fallback when [path] declines - which is what keeps a budget miss invisible: the NPC still moves, it
   * just moves greedily for a tick. Greedy in the old `Locomotion` sense, except that it now checks the step
   * is actually walkable instead of assuming it.
   */
  fun step(from: Vec3L, to: Vec3L): Vec3L? {
    val dx = (to.x - from.x).coerceIn(-1, 1)
    val dy = (to.y - from.y).coerceIn(-1, 1)
    if (dx == 0L && dy == 0L) return null

    val candidates = listOf(
      Vec3L(from.x + dx, from.y + dy, from.z),
      // Sidesteps, so a diagonal blocked by one corner still makes progress rather than standing still.
      Vec3L(from.x + dx, from.y, from.z),
      Vec3L(from.x, from.y + dy, from.z)
    )

    for (candidate in candidates) {
      if (candidate.x == from.x && candidate.y == from.y) continue
      if (!walkQuery.canStep(from, candidate)) continue
      val surface = walkQuery.surfaceAt(candidate) ?: continue
      return Vec3L(candidate.x, candidate.y, surface)
    }

    return null
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
