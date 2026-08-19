package net.bestia.zone.battle

import java.util.Random

/**
 * A [Random] whose every draw is [value].
 *
 * Damage is rolled four times - the hit check, the crit check, and two variance draws (the attack's and the
 * weapon's, the latter even when there is no weapon) - and every one reads `nextFloat()`, so pinning that one
 * method makes a whole swing deterministic. The value doubles as a choice of outcome: near 0 hits and crits at
 * full power, near 1 misses.
 */
class FixedRandom(private val value: Float) : Random() {

  override fun nextFloat(): Float = value

  /**
   * Long arithmetic on purpose: `1 shl 32` is 1 in Kotlin, since a shift count is taken modulo 32, so the
   * obvious Int version silently returns nonsense for the one width `nextInt()` and `nextLong()` ask for.
   */
  override fun next(bits: Int): Int {
    val max = (1L shl bits) - 1

    return (value.toDouble() * max).toLong().coerceIn(0L, max).toInt()
  }
}
