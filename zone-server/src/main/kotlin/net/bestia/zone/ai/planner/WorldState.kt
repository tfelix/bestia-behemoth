package net.bestia.zone.ai.planner

/**
 * An immutable symbolic world state: the truth value of each [StateKey]. Unlisted keys are treated
 * as `false`. Used both as a planning node and as a (partial) goal specification.
 */
data class WorldState(
  val facts: Map<StateKey, Boolean> = emptyMap()
) {

  operator fun get(key: StateKey): Boolean = facts[key] ?: false

  /** Returns a new state with [changes] applied on top of this one. */
  fun with(changes: Map<StateKey, Boolean>): WorldState = WorldState(facts + changes)

  /** True when every fact required by [goal] holds in this state. */
  fun satisfies(goal: WorldState): Boolean = goal.facts.all { (key, value) -> get(key) == value }

  /** Number of [goal] facts not yet met — the GOAP A* heuristic. */
  fun unmetCount(goal: WorldState): Int = goal.facts.count { (key, value) -> get(key) != value }

  companion object {
    fun of(vararg pairs: Pair<StateKey, Boolean>): WorldState = WorldState(mapOf(*pairs))
  }
}
