package net.bestia.worldgen.civ

import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.fields.Grid
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt

/**
 * A* over a movement-cost field.
 *
 * Roads are not straight lines between settlements; they are the cheapest way through the terrain, and that
 * is what makes them look like roads. Routing them over a cost field means they find the valley, contour
 * round the shoulder of a hill, skirt the marsh and cross the river at the narrow point - none of which has
 * to be written down anywhere, because all of it falls out of the cost field the habitability stage already
 * had to build.
 *
 * A* rather than Dijkstra because the endpoints are known and usually close. The heuristic is Euclidean
 * distance times the cheapest possible per-metre cost, which is admissible - it can never over-estimate -
 * so the path found is still optimal while typically expanding a small fraction of the map.
 *
 * ### Why the cost field alone is not enough
 *
 * A movement cost field is **isotropic**: it says how hard a cell is to be in, and nothing at all about which
 * way you are going through it. Contouring along a hillside and climbing straight up the same hillside cost
 * exactly the same, because they cross the same cells. Roads routed that way take the short way over a ridge
 * every time, since detouring round it is longer and the field gives back nothing for the gentler line - and
 * a road that climbs three hundred metres in three kilometres is one that has to be cut into the mountain
 * rather than laid on it.
 *
 * [elevation] fixes that by charging for the **grade of each step** on top of the cost of the ground it
 * crosses. Below [rulingGrade] a step is free of it, which is the grade a laden cart holds without help;
 * above it the multiplier climbs quadratically, so the search buys distance with the saving. That single term
 * is what makes a route follow a valley floor, take the pass rather than the summit, and swing wide round a
 * spur - the behaviours the surrounding prose already claimed and the isotropic field could not deliver.
 */
class RouteFinder(
  private val cost: Grid,
  private val metresPerCell: Double,
  /**
   * Cheapest per-metre cost anywhere in the field. The heuristic multiplier; must not exceed the true
   * minimum or the search stops being optimal.
   */
  private val minimumCost: Double = 1.0,
  /** Give up after expanding this many cells, so one impossible route cannot stall world generation. */
  private val expansionLimit: Int = 400_000,

  /**
   * Ground elevation per cell, on the same grid as [cost], or null to route on the cost field alone.
   *
   * Null is the old behaviour and stays the default: the sea lane search has no use for a grade, and the nav
   * graph re-routes hops that a road already decided the shape of.
   */
  private val elevation: Grid? = null,

  /**
   * Grade a step may hold without paying anything extra.
   *
   * This and the two below are inert without [elevation], so the values here are a shape rather than a
   * tuning: the world's numbers live in `SettlementParams.roadRulingGrade` and siblings, which is the object
   * whose digest moves `pipelineVersion` when they change. Retune there, not here.
   */
  private val rulingGrade: Double = 0.07,

  /** How hard the excess over [rulingGrade] is charged, per squared multiple of the ruling grade. */
  private val gradePenalty: Double = 9.0,

  /**
   * Ceiling on the grade multiplier, and therefore on how far the route will go round.
   *
   * This is the knob that decides what the search *buys* with the penalty: a factor of N says a step is worth
   * avoiding by up to N times the distance, so the ceiling is the longest detour any single steep step can
   * justify. Unbounded is not the same as ambitious - it is a road that will cross half a world rather than
   * climb a bank.
   *
   * It also keeps the term from swamping the ground it multiplies. `Terms.IMPASSABLE` is a finite 400 rather
   * than infinity so A* can still find a way across a strait, and the grade factor scales a *land* cost that
   * already runs to about 140 on the worst rock; the ceiling is what stops a mountainside being charged so far
   * past open water that the route would rather swim. It does not rule that out on its own at every setting -
   * measure the road count if you raise it, because a road whose route touches water is not a road here, it
   * is silently reclassified as a sea lane.
   */
  private val maxGradeFactor: Double = 8.0
) {

  init {
    require(elevation == null || (elevation.width == cost.width && elevation.height == cost.height)) {
      "elevation grid must match the cost grid, was ${elevation?.width}x${elevation?.height}"
    }
    require(rulingGrade > 0.0) { "rulingGrade must be positive, was $rulingGrade" }
    require(gradePenalty >= 0.0) { "gradePenalty must not be negative, was $gradePenalty" }
    require(maxGradeFactor >= 1.0) { "maxGradeFactor must be at least 1, was $maxGradeFactor" }
  }

  /** A found route: the cells it passes through, from start to goal, and what it cost. */
  class Route(val cells: IntArray, val cost: Double) {
    val length get() = cells.size
  }

  private val width = cost.width
  private val height = cost.height
  private val size = width * height

  /**
   * The cheapest route from [start] to [goal], or null when there is none within the expansion limit.
   *
   * Both are flat cell indices into the cost grid.
   */
  fun route(start: Int, goal: Int): Route? {
    if (start == goal) return Route(intArrayOf(start), 0.0)
    if (start !in 0 until size || goal !in 0 until size) return null

    val best = DoubleArray(size) { Double.MAX_VALUE }
    val cameFrom = IntArray(size) { -1 }
    val closed = BooleanArray(size)
    val open = DoubleIntHeap(1024)

    val goalX = goal % width
    val goalY = goal / width

    best[start] = 0.0
    open.push(heuristic(start % width, start / width, goalX, goalY), start)

    var expanded = 0
    while (!open.isEmpty) {
      val current = open.pop()
      if (closed[current]) continue
      if (current == goal) return reconstruct(cameFrom, start, goal, best[goal])

      closed[current] = true
      if (++expanded > expansionLimit) return null

      val x = current % width
      val y = current / width

      for (d in 0 until 8) {
        val nx = x + D8.DX[d]
        val ny = y + D8.DY[d]
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue

        val neighbour = ny * width + nx
        if (closed[neighbour]) continue

        // Averaging the two cells' cost rather than taking the destination's makes the field symmetric, so a
        // route costs the same in both directions - which matters because the trade network is undirected and
        // an asymmetric cost would give two different roads between the same pair of towns.
        val step = D8.LENGTH[d] * metresPerCell
        val tentative = best[current] +
            step * (cost.data[current] + cost.data[neighbour]) * 0.5 * gradeFactor(current, neighbour, step)

        if (tentative >= best[neighbour]) continue

        best[neighbour] = tentative
        cameFrom[neighbour] = current
        open.push(tentative + heuristic(nx, ny, goalX, goalY), neighbour)
      }
    }

    return null
  }

  /**
   * What the climb between two adjacent cells multiplies the cost of crossing them by.
   *
   * Symmetric by construction - it reads the absolute rise - which is the same property the averaged cell cost
   * above exists to preserve: the trade network is undirected, and a route that cost less one way round would
   * give two different roads between the same pair of towns.
   *
   * At or *above* one everywhere, it also leaves the heuristic admissible: [minimumCost] is a floor on the
   * per-metre cost, and a term that can only raise what a step costs cannot make the estimate an
   * over-estimate. A grade *discount* would, which is why descending is not cheaper here than climbing.
   */
  private fun gradeFactor(from: Int, to: Int, step: Double): Double {
    val z = elevation ?: return 1.0

    val grade = abs(z.data[to] - z.data[from]) / step
    val excess = grade - rulingGrade
    if (excess <= 0.0) return 1.0

    val over = excess / rulingGrade
    return min(maxGradeFactor, 1.0 + gradePenalty * over * over)
  }

  private fun heuristic(x: Int, y: Int, goalX: Int, goalY: Int): Double {
    val dx = (x - goalX).toDouble()
    val dy = (y - goalY).toDouble()
    return sqrt(dx * dx + dy * dy) * metresPerCell * minimumCost
  }

  private fun reconstruct(cameFrom: IntArray, start: Int, goal: Int, cost: Double): Route {
    val reversed = ArrayList<Int>()
    var at = goal
    while (at != -1) {
      reversed.add(at)
      if (at == start) break
      at = cameFrom[at]
    }
    reversed.reverse()
    return Route(reversed.toIntArray(), cost)
  }
}
