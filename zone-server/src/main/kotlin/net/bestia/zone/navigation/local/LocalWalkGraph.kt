package net.bestia.zone.navigation.local

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.navigation.graph.AStarGraph
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * The walkable ground around an entity, as a graph discovered one step at a time.
 *
 * **No grid is materialised, ever.** That is the difference from the `NavGrid` this replaces, which built a
 * fixed 100x100 array of tiles: the world is thousands of chunks across, so any fixed grid is either far too
 * small to path across a village or far too large to build. Neighbours are the eight adjacent columns, and
 * whether each is reachable is a question for [LocalWalkQuery] at the moment it is asked - against merged
 * voxels, so a wall a player built this minute is a wall.
 *
 * Searches are bounded by [bounds] as well as by A*'s own node limit. Both are needed and they fail
 * differently: the node limit stops an unreachable goal from expanding forever, and the box stops a
 * *reachable* goal from being reached the long way round through a neighbouring valley.
 */
class LocalWalkGraph(
  private val query: LocalWalkQuery,
  private val goal: Vec3L,
  /** Half-extent of the search box around the midpoint of start and goal, in position units. */
  private val bounds: Bounds
) : AStarGraph<Vec3L> {

  class Bounds(
    private val minX: Long,
    private val minY: Long,
    private val maxX: Long,
    private val maxY: Long
  ) {
    fun contains(point: Vec3L) = point.x in minX..maxX && point.y in minY..maxY

    companion object {
      /** A box containing both endpoints plus [margin] of slack, so a detour round an obstacle still fits. */
      fun around(from: Vec3L, to: Vec3L, margin: Long) = Bounds(
        minX = minOf(from.x, to.x) - margin,
        minY = minOf(from.y, to.y) - margin,
        maxX = maxOf(from.x, to.x) + margin,
        maxY = maxOf(from.y, to.y) + margin
      )
    }
  }

  override fun neighbors(node: Vec3L): List<Vec3L> {
    val found = ArrayList<Vec3L>(8)

    for (i in DX.indices) {
      // The vertical is the query's to decide, not the caller's: a step onto a stair is a step up, and
      // carrying the current z forward would ask whether a column is walkable at the wrong height.
      val candidate = Vec3L(node.x + DX[i], node.y + DY[i], node.z)
      if (!bounds.contains(candidate)) continue
      if (!query.canStep(node, candidate)) continue

      val surface = query.surfaceAt(candidate) ?: continue
      found.add(Vec3L(candidate.x, candidate.y, surface))
    }

    return found
  }

  /**
   * Distance, with a climb penalty.
   *
   * Whether a step is *possible* is settled before this is ever called - `canStep` already refused anything
   * steeper than the agent can manage. This only decides which of the legal ways round is nicer, and a route
   * that keeps to the flat looks more like something alive than one that goes straight over every mound.
   */
  override fun cost(from: Vec3L, to: Vec3L): Double {
    val dx = abs(to.x - from.x).toDouble()
    val dy = abs(to.y - from.y).toDouble()
    val flat = if (dx > 0.0 && dy > 0.0) DIAGONAL else 1.0
    val rise = abs(to.z - from.z).toDouble()

    return flat + rise * CLIMB_PENALTY
  }

  /**
   * Octile distance to the goal.
   *
   * Octile rather than Manhattan, and that matters: with diagonal moves available Manhattan *over*-estimates,
   * which makes the heuristic inadmissible and the "cheapest" path merely a plausible one. The old
   * `AStarPathfinder` used Manhattan on a four-connected grid, where it is exact - eight-connected it is not.
   *
   * The vertical is left out on purpose, so the estimate can never exceed the true remaining cost.
   */
  override fun heuristic(node: Vec3L, goal: Vec3L): Double {
    val dx = abs(goal.x - node.x).toDouble()
    val dy = abs(goal.y - node.y).toDouble()
    return max(dx, dy) + (DIAGONAL - 1.0) * minOf(dx, dy)
  }

  val goalPosition: Vec3L get() = goal

  companion object {
    private val DX = longArrayOf(-1, 0, 1, -1, 1, -1, 0, 1)
    private val DY = longArrayOf(-1, -1, -1, 0, 0, 1, 1, 1)

    private val DIAGONAL = sqrt(2.0)

    /**
     * What a voxel of climb costs relative to a step of travel.
     *
     * Small on purpose. Large enough that flat ground wins where flat ground exists, small enough that it
     * never turns a short climb into a long walk around a hill.
     */
    private const val CLIMB_PENALTY = 0.5
  }
}
