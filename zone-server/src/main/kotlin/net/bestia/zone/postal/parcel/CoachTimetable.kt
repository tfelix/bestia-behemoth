package net.bestia.zone.postal.parcel

import java.time.Duration
import java.time.Instant

/**
 * When the coach leaves and when it arrives, which together are a parcel's latest arrival time.
 *
 * Departures are on the hour the office level sets, counted from the epoch, so every office of a level leaves at
 * the same times and a player can learn them. The coach takes twice as long as a walking master, which leaves a
 * player courier one full walking time to spare.
 */
object CoachTimetable {

  /** A master walks about 4 m/s. */
  const val WALKING_KM_PER_HOUR = 14.4

  private const val COACH_SLOWDOWN = 2.0

  fun interval(level: Int): Duration {
    return when (level) {
      1 -> Duration.ofHours(6)
      2 -> Duration.ofHours(3)
      else -> Duration.ofHours(2)
    }
  }

  fun nextDeparture(level: Int, now: Instant): Instant {
    val every = interval(level).toMillis()
    val next = Math.floorDiv(now.toEpochMilli(), every) * every + every

    return Instant.ofEpochMilli(next)
  }

  fun coachTime(roadKm: Double): Duration {
    val hours = COACH_SLOWDOWN * roadKm / WALKING_KM_PER_HOUR
    return Duration.ofSeconds((hours * 3600).toLong())
  }

  fun latestArrival(originLevel: Int, roadKm: Double, now: Instant): Instant {
    return nextDeparture(originLevel, now).plus(coachTime(roadKm))
  }
}
