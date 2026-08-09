package net.bestia.zone.ai.core.action

import net.bestia.zone.ai.core.behavior.BtNode
import net.bestia.zone.ai.core.behavior.ImmediateSuccess
import net.bestia.zone.ai.core.effect.Effect
import net.bestia.zone.ai.core.precondition.Precondition
import net.bestia.zone.ai.core.state.WorldState

/**
 * A single, *grounded* step the planner can take: it is applicable when all its
 * [preconditions] hold, and when taken it transforms the world by folding its
 * [effects]. [cost] is a function of state so distance-based costs (e.g. walking
 * further is more expensive) are expressible.
 *
 * "Grounded" means fully concrete — `walkTo(market)`, not a parameterised
 * `walkTo(target)`. Grounding is the [ActionResolver]'s job and keeps the A*
 * branching factor finite.
 *
 * ### Planning contract vs runtime behaviour
 *
 * [preconditions]/[effects]/[cost] are what the planner reasons over; [behavior] is how the step is
 * actually carried out in the world, as a fresh behaviour tree the act stage ticks until it reports
 * SUCCESS or FAILURE. Holding both on the *grounded* action is the whole point of grounding: because
 * the action already knows its concrete target, it can hand that target to its tree
 * (`behavior = { MoveTo(spot.position) }`) instead of the tree having to rediscover it from shared
 * mutable state.
 *
 * [behavior] is a factory, not a node, because a tree may carry per-run state and each adoption of
 * the action needs its own instance.
 *
 * It defaults to [ImmediateSuccess] — an action that takes no time — so a purely symbolic domain can
 * omit it entirely and still be planned and simulated.
 */
class Action(
  val name: String,
  val preconditions: List<Precondition> = emptyList(),
  val effects: List<Effect> = emptyList(),
  val cost: (WorldState) -> Float = { 1f },
  val behavior: () -> BtNode = { ImmediateSuccess },
  /** What the body is doing while this step runs, for whoever renders it. See [Posture]. */
  val posture: Posture = Posture.ACTIVE,
) {
  fun isApplicable(state: WorldState): Boolean = preconditions.all { it.isSatisfied(state) }

  fun applyTo(state: WorldState): WorldState =
    effects.fold(state) { acc, effect -> effect.applyTo(acc) }

  override fun toString(): String = name
}
