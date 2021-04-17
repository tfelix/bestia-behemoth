package net.bestia.ai.planner.goap

import mu.KotlinLogging
import net.bestia.ai.planner.goap.condition.Condition
import java.time.Duration
import java.time.Instant

private val LOG = KotlinLogging.logger { }

class GoapPlanner(
    private val initialState: Set<Condition>,
    private val goalState: Set<Condition>,
    private val availableActions: MutableList<Action>
) {

  private data class TransitionNode(
      val parent: TransitionNode?,
      val action: Action,
      val cost: Int,
      val heuristic: Int
  )

  @ExperimentalStdlibApi
  fun plan(maxPlanningDuration: Duration): List<Action> {
    val start = Instant.now()

    // Find all possible actions for the initial state. Please note that set of actions can not change anymore
    // after they have been set so actually all possible actions must be given here.
    val initialPossibleActions = availableActions.filter { it.isPossible(initialState) }
    val openNodes = makeNodesFromActions(initialPossibleActions, null).toMutableList()

    var goalReached = false
    val bestActionPath = mutableListOf<Action>()

    while (!goalReached && openNodes.isNotEmpty() && Duration.between(start, Instant.now()) < maxPlanningDuration) {
      openNodes.sortBy { it.cost + it.heuristic }
      val bestCandidateNode = openNodes.removeFirst()

      // get all actions to the start from this candidate
      val actionsFromStart = getActionsFromStart(bestCandidateNode)

      // apply all action effects to get current world state
      val currentState = actionsFromStart.foldRight(initialState) { action, currentState -> action.applyEffects(currentState) }

      if (isGoalReached(currentState)) {
        goalReached = true
        bestActionPath.addAll(getActionsFromStart(bestCandidateNode))
        break
      }

      val currentPossibleActions = availableActions.filter { it.isPossible(currentState) }
      val currentPossibleTransitions = makeNodesFromActions(currentPossibleActions, bestCandidateNode)

      openNodes.addAll(currentPossibleTransitions)
    }

    if (!goalReached) {
      val timeUsed = Duration.between(start, Instant.now())
      LOG.debug { "Goal $goalState was not reached after $timeUsed" }
    }

    return bestActionPath
  }

  private fun makeNodesFromActions(actions: Collection<Action>, parent: TransitionNode?): List<TransitionNode> {
    return actions.map { a ->
      TransitionNode(
          parent = parent,
          action = a,
          cost = a.cost,
          heuristic = 0
      )
    }
  }

  private fun isGoalReached(currentState: Set<Condition>): Boolean {
    return goalState.all { gs -> gs.isFulfilledBy(currentState) }
  }

  private fun getActionsFromStart(node: TransitionNode): List<Action> {
    var t: TransitionNode? = node
    val path = mutableListOf<TransitionNode>()

    do {
      path.add(t!!)
      t = t.parent
    } while (t != null)

    return path.map { it.action }.reversed()
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