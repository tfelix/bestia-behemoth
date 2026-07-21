package net.bestia.zone.ai.goap2.effect

import net.bestia.zone.ai.goap2.state.StateKey

object Effects {

  /** Set [key] to a fixed [value]. */
  fun <T> set(key: StateKey<T>, value: T): Effect =
    Effect { it.with(key, value) }

  /**
   * Compute the new value from the old one, e.g. `modify(gold) { (it ?: 0) - price }`
   * or adding an item to an inventory set.
   */
  fun <T> modify(key: StateKey<T>, transform: (T?) -> T): Effect =
    Effect { it.with(key, transform(it.get(key))) }

  /** Remove [key] entirely (e.g. an item picked up leaves the world's ground). */
  fun remove(key: StateKey<*>): Effect =
    Effect { it.without(key) }
}