package net.bestia.zone.navigation.graph

import java.util.PriorityQueue

/**
 * A graph, described only as far as A* needs it.
 *
 * Both navigation tiers implement this: the macro tier over integer node ids, the local tier over voxel
 * columns it discovers as it goes. Neither has to materialise itself first, which is what lets the local
 * tier search a world four thousand chunks across without a grid existing anywhere.
 */
interface AStarGraph<N> {

  fun neighbors(node: N): List<N>

  /** Cost of the step from [from] to [to]. Must not be negative, or the search stops being correct. */
  fun cost(from: N, to: N): Double

  /**
   * Estimated remaining cost from [node] to [goal].
   *
   * Must never over-estimate. An inadmissible heuristic makes A* return a path that is merely plausible
   * rather than cheapest, which here would show up as NPCs taking visibly silly routes on some seeds only.
   */
  fun heuristic(node: N, goal: N): Double
}

/**
 * A*, shared by both navigation tiers.
 *
 * Replaces the hand-rolled `AStarPathfinder` that searched a fixed `NavGrid`. Two things changed and both
 * were needed: it is generic over the node type, because the macro graph's nodes are not grid cells; and the
 * open set is a heap rather than a list scanned with `minByOrNull`, which was O(V) per pop and therefore
 * O(V^2) overall - tolerable for a 100x100 grid that never existed outside a factory method, not for a real
 * search.
 */
object AStar {

  /**
   * The cheapest path from [start] to [goal] inclusive, or null when there is none within [nodeLimit].
   *
   * [nodeLimit] is a safety valve rather than a tuning knob. An unreachable goal - across water, behind a
   * collapsed bridge, or simply outside the loaded world - would otherwise expand until it ran out of graph,
   * and on the local tier "the graph" is every column in the world.
   */
  fun <N> findPath(
    graph: AStarGraph<N>,
    start: N,
    goal: N,
    nodeLimit: Int = 20_000
  ): List<N>? {
    require(nodeLimit > 0) { "nodeLimit must be positive, was $nodeLimit" }

    if (start == goal) return listOf(start)

    val cameFrom = HashMap<N, N>()
    val bestCost = HashMap<N, Double>()
    val closed = HashSet<N>()

    // Ordered by f, with the node carried alongside so the comparator never has to look anything up.
    val open = PriorityQueue<Entry<N>>(compareBy { it.f })

    bestCost[start] = 0.0
    open.add(Entry(start, graph.heuristic(start, goal)))

    var expanded = 0
    while (open.isNotEmpty()) {
      val current = open.poll().node

      if (current == goal) return reconstruct(cameFrom, start, goal)
      if (!closed.add(current)) continue
      if (++expanded > nodeLimit) return null

      val currentCost = bestCost[current] ?: continue

      for (next in graph.neighbors(current)) {
        if (next in closed) continue

        val stepCost = graph.cost(current, next)
        if (stepCost < 0.0) continue

        val tentative = currentCost + stepCost
        if (tentative >= (bestCost[next] ?: Double.MAX_VALUE)) continue

        bestCost[next] = tentative
        cameFrom[next] = current
        // Stale entries for `next` are left in the heap rather than removed - removal is O(n) and the
        // `closed` check above discards them on pop for nothing.
        open.add(Entry(next, tentative + graph.heuristic(next, goal)))
      }
    }

    return null
  }

  private class Entry<N>(val node: N, val f: Double)

  private fun <N> reconstruct(cameFrom: Map<N, N>, start: N, goal: N): List<N> {
    val path = ArrayList<N>()
    var at = goal
    while (true) {
      path.add(at)
      if (at == start) break
      at = cameFrom[at] ?: break
    }
    path.reverse()
    return path
  }
}
