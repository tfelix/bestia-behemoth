package net.bestia.zone.breeding

import java.time.Duration
import java.time.Instant

/** One Breeding Farm as the tick keeps it. Immutable: every change replaces it in [BreedingFarms]. */
data class BreedingFarm(
  val structureId: Long,
  val phase: FarmPhase = FarmPhase.IDLE,

  /** Stays after an egg is laid, so one more bundle breeds the same pair again. */
  val pair: BreedingPair? = null,
  val hasBundle: Boolean = false,

  /** How long the pair's clutch takes, after Bloodline Study. Set when the pair is sent. */
  val clutchTime: Duration = Duration.ZERO,
  val endsAt: Instant? = null,

  /** The farm owner's skills when the clutch started, since the owner may be offline when it is laid. */
  val dominantIvs: Int = 3,
  val beastfriendLevel: Int = 0,

  val egg: EggSummary? = null,
) {

  fun remainingAt(now: Instant): Duration {
    return when (phase) {
      FarmPhase.BREEDING -> Duration.between(now, endsAt ?: now).coerceAtLeast(Duration.ZERO)
      FarmPhase.GATHERING -> clutchTime
      else -> Duration.ZERO
    }
  }

  /** Nothing is going on, but a pair may still stand at the farm. */
  fun withoutClutch(): BreedingFarm {
    return copy(phase = FarmPhase.IDLE, endsAt = null)
  }
}
