package net.bestia.zone.ai.planner

import org.springframework.stereotype.Service
import java.util.PriorityQueue

/**
 * Forward A* GOAP planner. Searches the space of symbolic world states, expanding a state via every
 * applicable action, until a state satisfying the goal is reached. Uses the number of unmet goal
 * facts as an (admissible-enough) heuristic and a closed set of visited states plus an iteration cap
 * to stay bounded.
 */
@Service
class GoapPlanner : Planner {

  private class SearchNode(
    val state: WorldState,
    val gCost: Double,
    val heuristic: Int,
    val action: GoapAction?,
    val parent: SearchNode?
  ) {
    val fCost: Double get() = gCost + heuristic
  }

  override fun plan(start: WorldState, goal: WorldState, actions: List<GoapAction>): Plan? {
    if (start.satisfies(goal)) {
      return Plan(emptyList())
    }

    val open = PriorityQueue<SearchNode>(compareBy { it.fCost })
    val bestG = mutableMapOf<WorldState, Double>()

    val startNode = SearchNode(start, 0.0, start.unmetCount(goal), null, null)
    open.add(startNode)
    bestG[start] = 0.0

    var iterations = 0

    while (open.isNotEmpty()) {
      if (iterations++ > MAX_ITERATIONS) {
        return null
      }

      val current = open.poll()

      if (current.state.satisfies(goal)) {
        return Plan(reconstruct(current))
      }

      // Skip stale queue entries superseded by a cheaper path to the same state.
      if (current.gCost > (bestG[current.state] ?: Double.MAX_VALUE)) {
        continue
      }

      for (action in actions) {
        if (!action.isApplicable(current.state)) {
          continue
        }

        val nextState = action.apply(current.state)
        val nextG = current.gCost + action.cost

        if (nextG >= (bestG[nextState] ?: Double.MAX_VALUE)) {
          continue
        }

        bestG[nextState] = nextG
        open.add(SearchNode(nextState, nextG, nextState.unmetCount(goal), action, current))
      }
    }

    return null
  }

  private fun reconstruct(node: SearchNode): List<GoapAction> {
    val actions = ArrayDeque<GoapAction>()
    var current: SearchNode? = node
    while (current?.action != null) {
      actions.addFirst(current.action!!)
      current = current.parent
    }
    return actions.toList()
  }

  companion object {
    private const val MAX_ITERATIONS = 1000
  }
}
