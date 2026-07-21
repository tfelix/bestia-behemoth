package net.bestia.zone.ai.goap2.agent

import net.bestia.zone.ai.goap2.action.ActionResolver
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.state.Blackboard

/**
 * An actor in the world: a set of [goals] to weigh, a private [memory]
 * blackboard (perception the agent knows that the shared world may not), and the
 * [actionResolver] that grounds this agent's action templates against a state.
 */
class Agent(
  val name: String,
  val goals: List<Goal>,
  val memory: Blackboard,
  val actionResolver: ActionResolver,
)
