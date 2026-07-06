package net.bestia.zone.ai.planner

import net.bestia.zone.ai.behavior.BtNode

/**
 * A GOAP action: a symbolic transition ([preconditions] -> [effects]) with a [cost], paired with the
 * concrete behaviour ([behaviorTree]) that carries it out at runtime. The planner chains actions
 * purely on their symbolic contract; the behaviour tree is only ticked once the action is selected.
 *
 * Add a new action by dropping in a new bean; it becomes available to any archetype that lists its
 * [id] under `actions`.
 */
interface GoapAction {
  val id: String
  val preconditions: Map<StateKey, Boolean>
  val effects: Map<StateKey, Boolean>
  val cost: Double

  /** A fresh behaviour tree instance carrying out this action. */
  fun behaviorTree(): BtNode

  /** True when [state] meets every precondition. */
  fun isApplicable(state: WorldState): Boolean =
    preconditions.all { (key, value) -> state[key] == value }

  /** The state produced by applying this action's effects to [state]. */
  fun apply(state: WorldState): WorldState = state.with(effects)
}
