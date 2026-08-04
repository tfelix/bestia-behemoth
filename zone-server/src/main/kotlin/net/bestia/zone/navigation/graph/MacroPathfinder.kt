package net.bestia.zone.navigation.graph

import net.bestia.zone.navigation.profile.MovementProfile

/**
 * A* over the macro graph, filtered and weighted for one particular creature.
 *
 * This is where "wild things avoid roads and merchants seek them out" actually happens, and it is a per-query
 * weighting rather than a per-species graph. The generator emitted one set of routes with two facts on each
 * edge - what it demands and what it is - and everything species-specific is applied here, in the cost
 * function, at plan time. A wolf and a wagon search the same graph and get different answers.
 */
class MacroPathfinder(
  private val graph: RuntimeNavGraph,
  private val profile: MovementProfile,
  /** Whether an edge is currently usable at all. See [MacroGraphService]'s blocked overlay. */
  private val isBlocked: (Int) -> Boolean
) : AStarGraph<Int> {

  override fun neighbors(node: Int): List<Int> {
    val incident = graph.incidentEdges.getOrNull(node) ?: return emptyList()
    val found = ArrayList<Int>(incident.size)

    for (edgeIndex in incident) {
      if (isBlocked(edgeIndex)) continue

      val edge = graph.edges[edgeIndex]
      // The capability gate. `canTraverse` is a conjunction over the edge's demands, so a creature that
      // cannot swim is refused a river ford even though the ford is also walkable in part.
      if (!profile.canTraverse(edge.modes, edge.maxAgentHalfWidth)) continue

      found.add(edge.other(node))
    }

    return found
  }

  /**
   * Cheapest usable edge between two adjacent nodes, weighted by the profile.
   *
   * Cheapest rather than first, because two nodes can be joined more than once - a road and the open country
   * beside it both connect the same pair - and which one a creature would take is exactly the decision this
   * class exists to make.
   */
  override fun cost(from: Int, to: Int): Double {
    var best = Double.MAX_VALUE

    for (edgeIndex in graph.incidentEdges[from]) {
      if (isBlocked(edgeIndex)) continue

      val edge = graph.edges[edgeIndex]
      if (edge.other(from) != to) continue
      if (!profile.canTraverse(edge.modes, edge.maxAgentHalfWidth)) continue

      val weighted = edge.baseCost * profile.costMultiplier(edge.isMadeSurface)
      if (weighted < best) best = weighted
    }

    return if (best == Double.MAX_VALUE) UNREACHABLE else best
  }

  /**
   * Straight-line distance to the goal, scaled by the cheapest per-metre cost this creature could ever pay.
   *
   * Scaling is what keeps the estimate admissible under per-species weighting. The generator's own baseline is
   * one per metre off-road and less on a road; a merchant with a 0.4 road multiplier can pay less than that,
   * so an unscaled distance heuristic would over-estimate for exactly the creature the road network was built
   * for - and A* with an inadmissible heuristic returns a route that merely looks reasonable.
   */
  override fun heuristic(node: Int, goal: Int): Double {
    val from = graph.nodePositions[node]
    val to = graph.nodePositions[goal]

    val dx = (to.x - from.x).toDouble()
    val dy = (to.y - from.y).toDouble()
    val distance = Math.sqrt(dx * dx + dy * dy)

    return distance * cheapestPerMetre
  }

  private val cheapestPerMetre: Double =
    MADE_SURFACE_COST_PER_METRE * minOf(profile.roadCostMultiplier, profile.offRoadCostMultiplier)

  private companion object {
    /**
     * Marks a step nothing can take. Not `MAX_VALUE`, which overflows to infinity when added to a path cost
     * and then compares equal to every other unreachable step, hiding real routes behind arithmetic.
     */
    private const val UNREACHABLE = 1e12

    /**
     * The generator's cheapest possible per-metre baseline, from `NavParams.infrastructureCostPerMetre`.
     *
     * Duplicated as a constant rather than read from the graph, and it is the safe direction to be wrong in:
     * an under-estimate keeps the heuristic admissible, which is the property that matters. If the generator's
     * roads ever get cheaper than this, the search stays correct and merely explores a little more.
     */
    private const val MADE_SURFACE_COST_PER_METRE = 0.5
  }
}
