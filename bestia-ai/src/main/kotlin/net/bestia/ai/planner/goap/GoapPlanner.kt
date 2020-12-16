package net.bestia.ai.planner.goap

import net.bestia.ai.Consideration
import net.bestia.ai.planner.Planner

class GoapPlanner(
    private val worldState: Set<Precondition>,
    private val goalState: Map<String, Any>,
    private val availableActions: MutableList<Action>
) {

  private class Node(
      val parent: Node?,
      val action: Action,
      val next: Node?
  )

  fun plan(): Action? {
    // Find all possible actions for the current world state
    val possibleActions = availableActions.filter { it.isPossible(worldState) }
    // sort them by cost, lowest cost first

    val action = possibleActions.first()
    action.applyEffects(worldState)

    // Gather world variables


  }
}