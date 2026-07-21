package net.bestia.zone.ai.goap2.action

import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Produces the set of concrete, *grounded* actions available from a given
 * [WorldState]. The planner calls this at every node it expands, so actions that
 * only become possible in a future state (e.g. `pickUpItem` once the agent has
 * walked to the item) are discovered as the search progresses.
 *
 * This is an interface on purpose: the GOAP core stays domain-agnostic. A game
 * supplies its own resolver that grounds the domain's action templates
 * (`walkTo(location)` per known location, `buyItem(item)` per affordable item,
 * ...) against the current state. See the market scenario in the tests for a
 * worked example.
 *
 * Implementations should return a *bounded* list — grounding against discrete,
 * known targets is what keeps forward search tractable over continuous values
 * like positions.
 */
fun interface ActionResolver {
  fun getAvailableActions(state: WorldState): List<Action>
}
