package net.bestia.ai.planner.goap

class GoapPlanner(
    private val initialState: Set<Condition>,
    private val goalState: Set<Condition>,
    private val availableActions: MutableList<Action>
) {

  private class Node(
      val parent: Node?,
      val action: Action,
      val cost: Int,
      val heuristic: Int,
      val next: Node?
  )

  fun plan(): Action? {
    // Find all possible actions for the initial state. Please note that set of actions can not change anymore
    // after they have been set so actually all possible actions must be given here.
    val possibleActions = availableActions.filter { it.isPossible(initialState) }

    // sort them by cost, lowest cost first.
    val sortedActions = possibleActions.sortedBy { it.cost }

    // mutate worldstate by all actions and determine how "far" the new world state is away from the goal
    // set this as heuristic.
    val currentState = initialState.toMutableSet()
    /*
    val mutatedStates = sortedActions
        .map {
          it.applyEffects(currentState)
          Triple(it, currentState)
        }
        .map { }
*/

    // set this as the start action and sort them for total cost.

    // start to expand from the lowest cost

    // pick lowest cost action

    val action = possibleActions.first()
    // action.applyEffects(initialState)

    // Gather world variables
    return null
  }

  private fun getAccumulatedDistance(currentState: Set<Condition>): Int {
    var d = 0
    goalState.forEach { gs ->
      currentState.forEach { cs ->
        // gs.isFulfilledBy()

      }
    }

    return 0
  }
}