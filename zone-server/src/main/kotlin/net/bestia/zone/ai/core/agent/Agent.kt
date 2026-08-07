package net.bestia.zone.ai.core.agent

import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState

/**
 * Everything the planner needs to know about an actor: a set of [goals] to weigh, a private [memory]
 * blackboard (perception the agent knows that the shared world may not), and the [actionResolver] that
 * grounds this agent's action templates against a state.
 *
 * [teamMemory] is the shared board for this agent's pack/faction (e.g. a shared foraging map), or null
 * for agents that don't belong to one. The world-wide board is *not* held here — one instance is
 * shared by every agent, so it is passed in at plan time instead (see [snapshotState]).
 *
 * This is an interface so the planner stays free of the ECS: the live game's agent is an ECS
 * component that also carries plan-execution state, while a domain test uses the plain [SimpleAgent].
 * Both plan identically.
 */
interface Agent {
  val name: String
  val goals: List<Goal>
  val memory: Blackboard
  val actionResolver: ActionResolver
  val teamMemory: Blackboard? get() = null

  /** Layers [world] -> [teamMemory] -> [memory], most-specific-last so it wins on conflict. */
  fun snapshotState(world: Blackboard): WorldState =
    world.snapshotMergedWith(*listOfNotNull(teamMemory, memory).toTypedArray())
}

/**
 * A plain [Agent] with no ties to the ECS, for developing and testing a domain before any behaviour is
 * wired to it — which is exactly what the market simulation and the bestia domain scenarios do.
 */
class SimpleAgent(
  override val name: String,
  override val goals: List<Goal>,
  override val memory: Blackboard,
  override val actionResolver: ActionResolver,
  override val teamMemory: Blackboard? = null,
) : Agent
