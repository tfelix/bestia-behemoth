package net.bestia.zone.ai.ecs

import net.bestia.zone.ai.core.action.Action
import net.bestia.zone.ai.core.action.ActionResolver
import net.bestia.zone.ai.core.agent.Agent
import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.goal.Goal
import net.bestia.zone.ai.core.planner.Plan
import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.WorldState
import net.bestia.zone.ecs.core.Component

/**
 * All per-NPC AI state: the agent's identity for the planner ([goals], [memory], [actionResolver]) plus
 * the plan it is currently carrying out.
 *
 * Replaces the old `Brain`, which mixed these with static archetype configuration — wander radius, melee
 * range, flee threshold, attack cooldown — that a profile already held. Those now live where they belong:
 * tuning numbers are written into [memory] as permanent facts when the profile is attached, so goal
 * availability and priority read them the same way they read anything else, and there is one place to
 * change a number rather than two.
 *
 * ### Deliberately not [net.bestia.zone.ecs.core.Dirtyable]
 *
 * Not implementing `Dirtyable` is what keeps AI internals off the wire. (The old comment here claimed the
 * dirty-component scan was limited to `net.bestia.zone.ecs` and that the package therefore protected it —
 * that was wrong: `scanDirtyableComponentTypes()` scans all of `net.bestia.zone`. The interface, not the
 * package, is the boundary.) Client-visible effects still reach players for free through the dirtyable
 * `Path`/`Position`/`Health` components the behaviour trees mutate.
 */
class AiAgent(
  /** Which archetype this agent was built from, for logging and for re-attaching after persistence. */
  val profileId: String,
  override val name: String,
  override val goals: List<Goal>,
  override val actionResolver: ActionResolver,
  override val memory: Blackboard,
  override val teamMemory: Blackboard? = null,
) : Component, Agent {

  var currentGoal: Goal? = null
    private set

  var currentPlan: Plan? = null
    private set

  var planCursor: Int = 0
    private set

  /** Behaviour tree of the plan step being executed right now. */
  var currentActionNode: BtNode? = null
    private set

  /**
   * The state snapshot the current plan was built from, handed to each behaviour tick.
   *
   * A tree reads it rather than re-snapshotting, so every leaf in one action sees the same world the
   * planner reasoned about — a leaf that disagreed with the plan's premises would be acting on a
   * different world than the one that chose it.
   */
  var planState: WorldState = WorldState.EMPTY
    private set

  /**
   * Whether perception has ever looked at the world on this agent's behalf.
   *
   * Nothing may be planned before it has, and that is not a mere optimisation. An agent's memory starts empty,
   * and an empty memory is not a neutral starting point for a search — it is a set of unknowns the planner will
   * happily resolve by *assuming* an action's effects. A freshly spawned creature that did not know where it
   * was would plan `returnHome` first, not to travel but because arriving home is the only way it could learn a
   * position at all, and then chain a journey on top of that fiction.
   *
   * Refusing to reason before observing removes the whole class of problem, rather than patching each action
   * that happens to claim an observation.
   */
  var hasPerceived: Boolean = false

  /**
   * Earliest tick this agent may think again.
   *
   * Planning is the expensive half, so agents are spread across ticks rather than all replanning on the
   * same one. `World.tickCount` exists for exactly this and nothing used it before.
   */
  var nextThinkTick: Long = 0L

  fun currentAction(): Action? = currentPlan?.actions?.getOrNull(planCursor)

  fun hasActivePlan(): Boolean = currentActionNode != null

  /** Takes on [plan] for [goal], starting at its first step. */
  fun adopt(goal: Goal, plan: Plan, state: WorldState) {
    currentGoal = goal
    currentPlan = plan
    planState = state
    planCursor = 0
    currentActionNode = plan.actions.firstOrNull()?.behavior?.invoke()
  }

  /** Advances to the next step; returns its fresh behaviour tree, or null when the plan is finished. */
  fun advancePlan(): BtNode? {
    planCursor++
    val next = currentAction()
    currentActionNode = next?.behavior?.invoke()
    return currentActionNode
  }

  fun clearPlan() {
    currentGoal = null
    currentPlan = null
    planCursor = 0
    currentActionNode = null
    planState = WorldState.EMPTY
  }
}
