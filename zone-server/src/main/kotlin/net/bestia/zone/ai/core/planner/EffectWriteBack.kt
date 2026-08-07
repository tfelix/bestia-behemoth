package net.bestia.zone.ai.core.planner

import net.bestia.zone.ai.core.state.Blackboard
import net.bestia.zone.ai.core.state.MemoryScope
import net.bestia.zone.ai.core.state.StateKey
import net.bestia.zone.ai.core.state.WorldState

/**
 * Writes the difference between two [WorldState]s back into live memory, letting each key's
 * [MemoryScope] decide how far its new value cascades: always to the acting agent's own board,
 * additionally to its pack/faction board for [MemoryScope.TEAM] and up, and additionally to the
 * world-wide board for [MemoryScope.WORLD].
 *
 * This is what makes "the pack shares foraging spots" or "the species learns which attacks work"
 * fall out of an ordinary effect instead of needing bespoke broadcast code.
 *
 * It is a standalone helper rather than a [PlanExecutor] private because the two callers need it at
 * different granularities: [PlanExecutor] simulates a whole plan at once, while the live act system
 * applies exactly one action's effects at the moment that action's behaviour tree reports SUCCESS.
 * Both want identical cascade semantics.
 */
object EffectWriteBack {

  /**
   * Applies every key [before] and [after] disagree on. Keys absent from [after] are removed rather
   * than written, so an effect that drops a fact (an eaten vegetation spot) is honoured too.
   *
   * [team] may be null for an agent that belongs to no pack.
   */
  fun apply(
    before: WorldState,
    after: WorldState,
    individual: Blackboard,
    team: Blackboard? = null,
    world: Blackboard? = null,
  ) {
    val touchedKeys = before.keys() + after.keys()
    for (key in touchedKeys) {
      @Suppress("UNCHECKED_CAST")
      applyKey(key as StateKey<Any?>, before, after, individual, team, world)
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> applyKey(
    key: StateKey<T>,
    before: WorldState,
    after: WorldState,
    individual: Blackboard,
    team: Blackboard?,
    world: Blackboard?,
  ) {
    // Perception owns observed keys. The search was free to hypothesise about them; persisting that
    // hypothesis would make the agent believe something it only planned. See StateKey.observed.
    if (key.observed) return

    val beforeValue = before.get(key)
    val afterValue = after.get(key)
    if (beforeValue == afterValue) return

    val targets = buildList {
      add(individual)
      if (key.scope >= MemoryScope.TEAM) team?.let(::add)
      if (key.scope == MemoryScope.WORLD) world?.let(::add)
    }

    // The cast is safe under `contains`: a key present in the state holds a value of its own type,
    // which may itself legitimately be null.
    for (board in targets) {
      if (after.contains(key)) board.set(key, afterValue as T) else board.remove(key)
    }
  }
}
