package net.bestia.zone.ai.planner

/**
 * Produces a [Plan] leading from a start world state to a goal state using a set of available
 * actions, or null when the goal is unreachable.
 */
interface Planner {
  fun plan(start: WorldState, goal: WorldState, actions: List<GoapAction>): Plan?
}
