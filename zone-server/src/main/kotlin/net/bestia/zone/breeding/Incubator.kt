package net.bestia.zone.breeding

import java.time.Duration
import java.time.Instant

/** One Incubator as the tick keeps it. Immutable: every change replaces it in [Incubators]. */
data class Incubator(
  val structureId: Long,
  val egg: EggSummary? = null,

  /** Since when [egg] warms here; its earlier progress is in [EggSummary.hatchedMillis]. */
  val since: Instant? = null,

  /** The owner's Bloodline Study when the egg was put in, since the owner may be offline when it hatches. */
  val reductionPercent: Int = 0,

  /** The bestia is being created off the tick. */
  val hatching: Boolean = false,

  /** Ready, but its owner has no free bestia slot; tried again at [retryAt]. */
  val waitingForSlot: Boolean = false,
  val retryAt: Instant? = null,
) {

  /** Hatch progress in base time: a reduced timer makes each real second count for more. */
  fun hatchedMillisAt(now: Instant): Long {
    val egg = egg ?: return 0
    val warmedMillis = Duration.between(since ?: now, now).toMillis().coerceAtLeast(0)
    val reduction = reductionPercent.coerceIn(0, BreedingTimes.MAX_REDUCTION_PERCENT)

    return (egg.hatchedMillis + warmedMillis * 100 / (100 - reduction)).coerceAtMost(egg.hatchMillis)
  }

  fun isReadyAt(now: Instant): Boolean {
    val egg = egg ?: return false
    return hatchedMillisAt(now) >= egg.hatchMillis
  }

  /** Real time left, at this incubator's speed. */
  fun remainingAt(now: Instant): Duration {
    val egg = egg ?: return Duration.ZERO
    val reduction = reductionPercent.coerceIn(0, BreedingTimes.MAX_REDUCTION_PERCENT)

    return Duration.ofMillis((egg.hatchMillis - hatchedMillisAt(now)) * (100 - reduction) / 100)
  }

  fun empty(): Incubator {
    return Incubator(structureId)
  }
}
