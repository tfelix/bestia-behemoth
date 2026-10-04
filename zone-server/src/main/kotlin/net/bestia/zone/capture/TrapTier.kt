package net.bestia.zone.capture

/**
 * The four Magic Bestia Traps of `bestia-docs/content/docs/mechanics/bestia.md`.
 *
 * Every row of the docs' base table is the tier's Lv. 1-20 value minus 40 points per level band, so a tier
 * is one number rather than a row of five.
 */
enum class TrapTier(
  /** The base catch chance against a Lv. 1-20 bestia, in percentage points. */
  val youngBase: Int
) {
  BESTIA_TRAP(60),
  SUPER_TRAP(100),
  MEGA_TRAP(140),
  MASTER_TRAP(180);

  /** The base catch chance against a bestia of [level], in percentage points; negative is allowed. */
  fun baseChance(level: Int): Int {
    val band = when {
      level <= 20 -> 0
      level <= 40 -> 1
      level <= 60 -> 2
      else -> 3
    }
    val base = youngBase - BAND_PENALTY * band

    // Continues the Lv. 61-100 column without a jump: the penalty term is zero at exactly Lv. 100.
    return if (level > 100) {
      base - OLD_AGE_PENALTY * (level - 100)
    } else {
      base
    }
  }

  private companion object {
    const val BAND_PENALTY = 40
    const val OLD_AGE_PENALTY = 3
  }
}
