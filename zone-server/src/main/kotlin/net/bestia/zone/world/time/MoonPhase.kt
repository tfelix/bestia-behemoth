package net.bestia.zone.world.time

import kotlin.math.PI
import kotlin.math.cos

/**
 * How the moon looks, in four broad phases. The moon goes round once per Bestia month; the client's `MoonCycle`
 * draws it from the same world age, so what the sky shows and what the server reads agree.
 */
enum class MoonPhase {
  NEW,
  WAXING,
  FULL,
  WANING;

  companion object {
    /** [cycle] runs 0 to 1 from one new moon to the next; 0.5 is full. */
    fun of(cycle: Double): MoonPhase {
      val lit = illuminationOf(cycle)
      return when {
        lit >= FULL_FROM -> FULL
        lit <= NEW_UNTIL -> NEW
        cycle < 0.5 -> WAXING
        else -> WANING
      }
    }

    fun illuminationOf(cycle: Double): Double = (1.0 - cos(2.0 * PI * cycle)) / 2.0

    /** About four Bestia days of each month count as full, and four as new. */
    private const val FULL_FROM = 0.85
    private const val NEW_UNTIL = 0.15
  }
}
