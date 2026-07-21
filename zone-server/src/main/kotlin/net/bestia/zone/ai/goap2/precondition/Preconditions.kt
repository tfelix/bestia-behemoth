package net.bestia.zone.ai.goap2.precondition

import net.bestia.zone.ai.goap2.state.StateKey

/**
 * Factory helpers with clear, non-inverted names (the old
 * `BiggerPrecondition`/`SmallerPrecondition` read backwards and didn't even
 * compile). Semantics are always stated from the *state value's* perspective:
 * `atLeast(hunger, 50)` means "the hunger stored in state is >= 50".
 */
object Preconditions {

  fun <T> equalTo(key: StateKey<T>, expected: T): Precondition =
    PredicatePrecondition(key, "$key == $expected") { it == expected }

  fun <T> satisfies(key: StateKey<T>, description: String, predicate: (T?) -> Boolean): Precondition =
    PredicatePrecondition(key, description, predicate)

  fun atLeast(key: StateKey<Int>, min: Int): Precondition =
    PredicatePrecondition(key, "$key >= $min") { it != null && it >= min }

  fun atMost(key: StateKey<Int>, max: Int): Precondition =
    PredicatePrecondition(key, "$key <= $max") { it != null && it <= max }

  fun greaterThan(key: StateKey<Int>, value: Int): Precondition =
    PredicatePrecondition(key, "$key > $value") { it != null && it > value }

  fun lessThan(key: StateKey<Int>, value: Int): Precondition =
    PredicatePrecondition(key, "$key < $value") { it != null && it < value }
}