package net.bestia.zone.ai.goap2.goal

import net.bestia.zone.ai.goap2.precondition.Precondition
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * Something an agent wants to be true about the world.
 *
 * Two concerns that the original blueprint conflated are separated here:
 *  - [availability]: should the agent even *consider* this goal right now?
 *  - [desiredState]: what does *success* look like? This is a conjunction of
 *    [Precondition]s that the planner searches to satisfy, and it doubles as the
 *    A* goal test and heuristic source.
 *
 * [priority] is a small declarative formula (see the [priority] DSL) rather than a bare number, and
 * all priority math is done in floating point, fixing the integer-division truncation in the
 * original.
 */
class Goal(
  val name: String,
  val priority: Priority,
  val availability: Precondition,
  val desiredState: List<Precondition>,
) {

  fun isAvailable(state: WorldState): Boolean = availability.isSatisfied(state)

  /** True once every desired condition holds — the A* goal test. */
  fun isSatisfiedBy(state: WorldState): Boolean = desiredState.all { it.isSatisfied(state) }

  /**
   * Admissible-ish A* heuristic: the number of desired conditions not yet
   * satisfied. It never over-counts the remaining conditions, which keeps the
   * search well-behaved (each action satisfies at most a handful of conditions).
   */
  fun heuristic(state: WorldState): Int = desiredState.count { !it.isSatisfied(state) }

  /** Dynamic priority for the current [state] — see [Priority.evaluate]. */
  fun evaluatePriority(state: WorldState): Float = priority.evaluate(state)

  /** A copy of this goal with its priority's base value overridden, e.g. per-archetype tuning. */
  fun withBasePriority(base: Float): Goal =
    Goal(name, Priority(base, priority.combine, priority.considerations), availability, desiredState)

  override fun toString(): String = name
}
