package net.bestia.zone.ai.goap2.planner

import net.bestia.zone.ai.goap2.action.Action
import net.bestia.zone.ai.goap2.goal.Goal

/**
 * The result of a successful search: the ordered [actions] that take the agent
 * from the start state to one satisfying [goal], plus the summed [totalCost].
 */
data class Plan(
  val goal: Goal,
  val actions: List<Action>,
  val totalCost: Float,
) {
  val isEmpty: Boolean get() = actions.isEmpty()

  override fun toString(): String =
    "Plan(goal=${goal.name}, cost=$totalCost, steps=[${actions.joinToString(" -> ") { it.name }}])"
}
