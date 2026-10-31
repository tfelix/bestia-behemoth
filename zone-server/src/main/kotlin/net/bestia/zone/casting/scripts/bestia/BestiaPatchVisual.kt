package net.bestia.zone.casting.scripts.bestia

import net.bestia.zone.battle.Element

/**
 * The client effect visual a bestia area skill leaves on the ground. Only Ember's burning patch exists so far
 * (`1_ember_patch.tres` in the client's `EffectVisual/DB`), so every element borrows it until it gets its own.
 */
object BestiaPatchVisual {

  private const val EMBER_PATCH = 1L

  @Suppress("UNUSED_PARAMETER")
  fun idOf(element: Element): Long {
    return EMBER_PATCH
  }
}
