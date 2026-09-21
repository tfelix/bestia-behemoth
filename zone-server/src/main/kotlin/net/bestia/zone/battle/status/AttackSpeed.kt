package net.bestia.zone.battle.status

/**
 * How long a swing takes, following Ragnarok Online's pre-renewal model so the numbers stay comparable to a
 * well-understood reference.
 *
 * The authored number is a *motion*, and the interval between two swings is twice it (rAthena's
 * `AMOTION_DIVIDER_PC 2`). ASPD is only ever the displayed reading of the motion, which is why nothing here
 * stores one: at ASPD 150 an entity swings once a second, at the 190 cap five times.
 */
object AttackSpeed {

  /** Motion at ASPD 0, and what one point of ASPD is worth - rAthena's `AMOTION_ZERO_ASPD`/`AMOTION_INTERVAL`. */
  const val ZERO_ASPD_MOTION_MS = 2000
  const val MS_PER_ASPD_POINT = 10

  /** ASPD 190, the fastest anything may swing. */
  const val MIN_MOTION_MS = 100

  /** The swing an entity with no weapon has; a weapon row replaces this once equipment exists. */
  const val BARE_HANDED_MOTION_MS = 700

  /** [baseMotionMs] reduced by AGI and DEX, per rAthena's pre-renewal `status_base_amotion_pc`. */
  fun motionMs(baseMotionMs: Int, sv: StatusValues): Int {
    val reduced = baseMotionMs - baseMotionMs * (4 * sv.agility + sv.dexterity) / 1000

    return reduced.coerceIn(MIN_MOTION_MS, ZERO_ASPD_MOTION_MS)
  }

  /** The displayed reading of [motionMs]. Nothing in combat routes on it; it exists to be shown and tested against. */
  fun aspd(motionMs: Int): Int {
    return (ZERO_ASPD_MOTION_MS - motionMs) / MS_PER_ASPD_POINT
  }

  /** Seconds from one swing to the next: twice the motion. */
  fun delaySeconds(baseMotionMs: Int, sv: StatusValues): Float {
    return 2f * motionMs(baseMotionMs, sv) / 1000f
  }
}
