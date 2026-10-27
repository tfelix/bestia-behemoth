package net.bestia.zone.item.equip

import net.bestia.zone.world.time.BestiaDateTime

/**
 * When a worn light source is lit and how fast it burns. Lighting and burning ask the same rule, so a client
 * never sees a light that does not burn, or a torch that burns in the dark unseen.
 */
object LightSourceRules {

  /**
   * Below this much daylight a worn light is lit. Half way through dusk, not only in full night: the last hour
   * before night is already too dark to see by.
   */
  private const val DARK_BELOW_DAYLIGHT = 0.5

  /** Real seconds per point of wear, so a torch's `max-durability` reads as its burn time. */
  const val BURN_INTERVAL_SECONDS = 10f

  fun isLit(now: BestiaDateTime): Boolean = now.daylight < DARK_BELOW_DAYLIGHT
}
