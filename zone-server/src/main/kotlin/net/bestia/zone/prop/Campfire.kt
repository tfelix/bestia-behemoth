package net.bestia.zone.prop

import java.time.Duration
import java.time.Instant

/**
 * How a campfire burns. It is the one station that can stop working: it burns for a while, goes cold, and is gone
 * if nobody adds wood in time.
 *
 * Everything here is worked out from [StructureEntry.burnsOutAt] and the time, never counted down, so a fire keeps
 * burning down while nobody is near enough for it to be in the world.
 */
object Campfire {

  /** How long a freshly built campfire burns. */
  val FIRST_BURN: Duration = Duration.ofMinutes(30)

  /** What one log adds. */
  val PER_LOG: Duration = Duration.ofMinutes(30)

  /** The most burn time a fire can hold, so one fire cannot be stacked up to burn for days. */
  val MAX_BURN: Duration = Duration.ofHours(2)

  /** A fire with less than this left runs low: the bestia working it puts on a log, so the queue never stops. */
  val LOW: Duration = Duration.ofMinutes(5)

  /** How long a cold campfire waits for wood before it is gone. */
  val COLD_UNTIL_GONE: Duration = Duration.ofMinutes(30)

  /** The two looks of its `prop-kinds.yml` row. */
  const val COLD_VARIANT = 0
  const val LIT_VARIANT = 1

  /**
   * When a fire that burns out at [burnsOutAt] burns out once a log is added at [now], or null when it holds
   * [MAX_BURN] already. A cold fire is lit again from now.
   */
  fun withLog(burnsOutAt: Instant, now: Instant): Instant? {
    val from = maxOf(burnsOutAt, now)
    val cap = now + MAX_BURN
    if (from >= cap) return null

    return minOf(from + PER_LOG, cap)
  }

  fun runsLowAt(entry: StructureEntry, now: Instant): Boolean {
    return entry.burnsOutAt != null && secondsLeft(entry.burnsOutAt, now) < LOW.seconds
  }

  fun variantAt(entry: StructureEntry, now: Instant): Int {
    return if (entry.canWorkAt(now)) LIT_VARIANT else COLD_VARIANT
  }

  /** Whole seconds of fire left, 0 once it is out. */
  fun secondsLeft(burnsOutAt: Instant, now: Instant): Long {
    return Duration.between(now, burnsOutAt).seconds.coerceAtLeast(0)
  }
}
