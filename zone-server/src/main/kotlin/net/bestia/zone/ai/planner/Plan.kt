package net.bestia.zone.ai.planner

/**
 * An ordered sequence of GOAP actions that leads from the current world state to a goal state. An
 * empty plan means the goal was already satisfied; a null plan (returned by the planner) means the
 * goal is unreachable with the available actions.
 */
data class Plan(
  val actions: List<GoapAction>
) {
  val isEmpty: Boolean get() = actions.isEmpty()
}
