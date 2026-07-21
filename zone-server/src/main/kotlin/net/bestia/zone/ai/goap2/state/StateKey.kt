package net.bestia.zone.ai.goap2.state

/**
 * A typed handle into a [WorldState] / [Blackboard].
 *
 * The type parameter [T] is *phantom* (it is not stored) but it lets the rest
 * of the system stay type-safe: `WorldState.get(hunger)` returns an `Int?` and
 * `WorldState.get(position)` returns a `Vector2?` without any casting at the
 * call site. This is what lets the same store hold both simple numerics
 * (Int 0..100) and complex objects (Vector2, item/location collections).
 *
 * Equality/hash are by [name] only, so two `StateKey<Int>("hunger")` created in
 * different places refer to the same slot.
 */
data class StateKey<T>(val name: String) {
  override fun toString(): String = name
}
