package net.bestia.zone.ai.core.behavior

/**
 * A node in a behaviour tree. A planned [net.bestia.zone.ai.core.action.Action] supplies a small tree
 * that the act stage ticks every frame while that action is current.
 *
 * ### A node may hold state, for exactly one adoption of its action
 *
 * `Action.behavior` is a *factory*, so each time an agent adopts an action it gets its own tree
 * instance, discarded when the action completes or the plan is abandoned. That makes per-node state
 * legitimate and bounded: a cooldown's remaining time or a repeat's counter lives as long as the
 * action is current and no longer. Nodes that need no state should still derive everything from
 * [BtContext] so they stay re-tickable.
 *
 * This interface and [Status] live in `ai/core` rather than `ai/bt` on purpose: `Action` has to be
 * able to name the behaviour that carries it out, and `BtContext` has to be able to name the core's
 * `Blackboard`/`WorldState`. Keeping the two-type contract here lets the tree *library* in `ai/bt`
 * depend on the core without the core depending back on it.
 */
interface BtNode {
  fun tick(context: BtContext): Status
}

enum class Status {
  SUCCESS,
  FAILURE,
  RUNNING
}

/**
 * The behaviour of an action that takes no time to carry out — it simply succeeds on the first tick,
 * letting the act stage apply the action's effects and move straight on to the next plan step.
 *
 * This is the default behaviour of an [net.bestia.zone.ai.core.action.Action], which is what lets a
 * purely symbolic domain (the market simulation in the tests) declare actions without inventing a
 * tree for each one, while a real in-world action opts into a multi-tick tree instead.
 */
object ImmediateSuccess : BtNode {
  override fun tick(context: BtContext): Status = Status.SUCCESS
}
