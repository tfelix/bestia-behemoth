package net.bestia.zone.ai.goap2.state

/**
 * How widely a [StateKey]'s value should be shared when
 * [net.bestia.zone.ai.goap2.planner.PlanExecutor] writes an action's effect back to memory. Ordered
 * narrowest to widest so a plain `>=` comparison decides which boards a write cascades to:
 * [INDIVIDUAL] only ever updates the acting agent's own memory; [TEAM] also updates its pack/faction
 * board; [WORLD] also updates the single world-wide board.
 */
enum class MemoryScope {
  INDIVIDUAL,
  TEAM,
  WORLD,
}
