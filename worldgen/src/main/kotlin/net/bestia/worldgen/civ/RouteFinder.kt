package net.bestia.worldgen.civ

import net.bestia.worldgen.fields.D8
import net.bestia.worldgen.fields.DoubleIntHeap
import net.bestia.worldgen.fields.Grid
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
  private val expansionLimit: Int = 400_000
) {

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
            step * (cost.data[current] + cost.data[neighbour]) * 0.5

        if (tentative >= best[neighbour]) continue

        best[neighbour] = tentative
        cameFrom[neighbour] = current
        open.push(tentative + heuristic(nx, ny, goalX, goalY), neighbour)
      }
    }

    return null
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
