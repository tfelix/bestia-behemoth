package net.bestia.zone.ai.goap2.precondition

import net.bestia.zone.ai.goap2.state.StateKey
import net.bestia.zone.ai.goap2.state.WorldState

/**
 * The one general-purpose precondition: read the (typed) value at [key] and run
 * an arbitrary [predicate] over it. Because the value is fully typed, this
 * handles *any* kind of memory uniformly:
 *
 * ```
 * PredicatePrecondition(position) { it != null && it.distanceTo(market) < 1f }
 * PredicatePrecondition(inventory) { FOOD in (it ?: emptySet()) }
 * ```
 *
 * The numeric helpers in [Preconditions] are thin wrappers around this.
 */
class PredicatePrecondition<T>(
  private val key: StateKey<T>,
  private val description: String,
  private val predicate: (T?) -> Boolean,
) : Precondition {
  override fun isSatisfied(state: WorldState): Boolean = predicate(state.get(key))
  override fun toString(): String = description
}