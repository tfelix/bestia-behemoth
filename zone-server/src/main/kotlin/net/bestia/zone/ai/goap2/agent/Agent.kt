package net.bestia.zone.ai.goap2.agent

import net.bestia.zone.ai.goap2.action.ActionResolver
import net.bestia.zone.ai.goap2.goal.Goal
import net.bestia.zone.ai.goap2.state.Blackboard
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * An actor in the world: a set of [goals] to weigh, a private [memory]
 * blackboard (perception the agent knows that the shared world may not), and the
 * [actionResolver] that grounds this agent's action templates against a state.
 *
 * [teamMemory] is the shared board for this agent's pack/faction (e.g. a shared foraging map), or
 * null for agents that don't belong to one. The world-wide board is *not* held here — one instance is
 * shared by every agent, so it is passed in at plan/execute time instead (see [snapshotState]).
 */
class Agent(
  val name: String,
  val goals: List<Goal>,
  val memory: Blackboard,
  val actionResolver: ActionResolver,
  val teamMemory: Blackboard? = null,
) {

  /** Layers [world] -> [teamMemory] -> [memory], most-specific-last so it wins on conflict. */
  fun snapshotState(world: Blackboard): WorldState =
    world.snapshotMergedWith(*listOfNotNull(teamMemory, memory).toTypedArray())
}
