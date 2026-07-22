package net.bestia.zone.environment.time

import java.time.Duration
import java.time.Instant

/**
 * A single point in the in-game "Bestia time" calendar, derived from how much real-world
 * time has elapsed since a world's creation instant. Per
 * https://docs.bestia-game.net/docs/mechanics/environment/#in-game-time, Bestia time runs
 * [SPEED_FACTOR]x faster than real time: a Bestia day is [HOURS_PER_DAY] Bestia-hours but
 * only takes 8 real-world hours to pass (2 real hours of night, 6 of day), a Bestia month is
 * [DAYS_PER_MONTH] Bestia-days (10 real-world days), and a Bestia year is [MONTHS_PER_YEAR]
 * months/seasons.
 *
 * The docs don't state which clock hour the night portion starts at, so this assumes
 * midnight, i.e. hours `[0, NIGHT_HOURS)` are night and the rest is day - see [isNight].
 */
data class BestiaDateTime(
  val year: Long,
  val month: Int,
  val day: Int,
  val hour: Int,
  val minute: Int,
  val second: Int,
) {

  init {
    require(year >= 1) { "year must be >= 1, was $year" }
    require(month in 1..MONTHS_PER_YEAR) { "month must be in 1..$MONTHS_PER_YEAR, was $month" }
    require(day in 1..DAYS_PER_MONTH) { "day must be in 1..$DAYS_PER_MONTH, was $day" }
    require(hour in 0 until HOURS_PER_DAY) { "hour must be in 0..${HOURS_PER_DAY - 1}, was $hour" }
    require(minute in 0..59) { "minute must be in 0..59, was $minute" }
    require(second in 0..59) { "second must be in 0..59, was $second" }
  }

  /** The current season, derived from [month]. */
  val season: Season get() = Season.ofMonth(month)

  /** Fraction of the current Bestia day elapsed, in `[0, 1)`. `0.0` is midnight. */
  val timeOfDay: Double
    get() = (hour * SECONDS_PER_HOUR + minute * 60 + second) / SECONDS_PER_DAY.toDouble()

  /** True during the [NIGHT_HOURS]-Bestia-hour night portion of the day (hours `[0, NIGHT_HOURS)`). */
  val isNight: Boolean get() = hour < NIGHT_HOURS

  /** True during the daytime portion of the day. */
  val isDay: Boolean get() = !isNight

  /** Fraction of the current season/month elapsed, in `[0, 1)`. */
  val seasonProgress: Double
    get() = ((day - 1) * SECONDS_PER_DAY + hour * SECONDS_PER_HOUR + minute * 60 + second) /
      (DAYS_PER_MONTH * SECONDS_PER_DAY).toDouble()

  /** Fraction of the current Bestia year elapsed, in `[0, 1)`. */
  val yearProgress: Double
    get() = (((month - 1) * DAYS_PER_MONTH + (day - 1)) * SECONDS_PER_DAY +
      hour * SECONDS_PER_HOUR + minute * 60 + second) /
      (MONTHS_PER_YEAR * DAYS_PER_MONTH * SECONDS_PER_DAY).toDouble()

  companion object {
    /** Bestia time passes this many times faster than real-world time. */
    const val SPEED_FACTOR = 3.0

    const val HOURS_PER_DAY = 24
    const val DAYS_PER_MONTH = 30
    const val MONTHS_PER_YEAR = 4

    /** Bestia-hours of night at the start of each day: 2 real-world hours * [SPEED_FACTOR]. */
    const val NIGHT_HOURS = 6

    private const val SECONDS_PER_HOUR = 3600L
    private const val SECONDS_PER_DAY = HOURS_PER_DAY * SECONDS_PER_HOUR
    private const val DAYS_PER_YEAR = DAYS_PER_MONTH * MONTHS_PER_YEAR

    /** The Bestia date/time [speedFactor]x real-time-speed [elapsed] real-world duration after a world's creation. */
    fun since(elapsed: Duration, speedFactor: Double = SPEED_FACTOR): BestiaDateTime {
      val safeElapsed = if (elapsed.isNegative) Duration.ZERO else elapsed
      val realSeconds = safeElapsed.seconds + safeElapsed.nano / 1_000_000_000.0
      val totalBestiaSeconds = (realSeconds * speedFactor).toLong()

      val totalDays = totalBestiaSeconds / SECONDS_PER_DAY
      val secondsIntoDay = totalBestiaSeconds % SECONDS_PER_DAY

      val year = totalDays / DAYS_PER_YEAR + 1
      val dayOfYear = totalDays % DAYS_PER_YEAR
      val month = (dayOfYear / DAYS_PER_MONTH).toInt()
      val dayOfMonth = (dayOfYear % DAYS_PER_MONTH).toInt()

      return BestiaDateTime(
        year = year,
        month = month + 1,
        day = dayOfMonth + 1,
        hour = (secondsIntoDay / SECONDS_PER_HOUR).toInt(),
        minute = ((secondsIntoDay % SECONDS_PER_HOUR) / 60).toInt(),
        second = (secondsIntoDay % 60).toInt(),
      )
    }

    /** The Bestia date/time at [now], given the world was created at [worldEpoch]. */
    fun at(worldEpoch: Instant, now: Instant, speedFactor: Double = SPEED_FACTOR): BestiaDateTime =
      since(Duration.between(worldEpoch, now), speedFactor)
  }
}
