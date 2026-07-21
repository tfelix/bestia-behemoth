package net.bestia.zone.ai.goap2.precondition

import net.bestia.zone.ai.goap2.state.WorldState

/**
 * A read-only test over a [WorldState]. Preconditions gate actions and describe
 * the desired state of a goal. They never mutate state.
 */
fun interface Precondition {
  fun isSatisfied(state: WorldState): Boolean
}
