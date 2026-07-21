package net.bestia.zone.ai.goap2.action

import net.bestia.zone.ai.goap2.effect.Effect
import net.bestia.zone.ai.goap2.precondition.Precondition
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * A single, *grounded* step the planner can take: it is applicable when all its
 * [preconditions] hold, and when taken it transforms the world by folding its
 * [effects]. [cost] is a function of state so distance-based costs (e.g. walking
 * further is more expensive) are expressible.
 *
 * "Grounded" means fully concrete — `walkTo(market)`, not a parameterised
 * `walkTo(target)`. Grounding is the [ActionResolver]'s job and keeps the A*
 * branching factor finite.
 */
class Action(
  val name: String,
  val preconditions: List<Precondition> = emptyList(),
  val effects: List<Effect> = emptyList(),
  val cost: (WorldState) -> Float = { 1f },
) {
  fun isApplicable(state: WorldState): Boolean = preconditions.all { it.isSatisfied(state) }

  fun applyTo(state: WorldState): WorldState =
    effects.fold(state) { acc, effect -> effect.applyTo(acc) }

  override fun toString(): String = name
}
