package net.bestia.zone.ai.goap2.effect

import net.bestia.zone.ai.goap2.state.WorldState

/**
 * A state transition. Given a [WorldState] it returns the *new* state that
 * results from an action taking place. Effects are the piece the original
 * blueprint was missing entirely — without them the planner has no way to
 * simulate the future, and therefore no way to search.
 *
 * Effects are pure functions of state, so applying them during A* never touches
 * the live blackboard.
 */
fun interface Effect {
  fun applyTo(state: WorldState): WorldState
}

