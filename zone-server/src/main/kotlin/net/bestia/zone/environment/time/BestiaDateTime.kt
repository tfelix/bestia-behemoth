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
 * ### The day has four parts, not two
 *
 * The docs give a night *length* and not a placement, and this used to put night at hours `[0, 6)` - which
 * kept the arithmetic simple and meant the sun snapped on at 06:00 and off at midnight. Night now straddles
 * midnight the way a night does, and the two hours on either side of it are twilight:
 *
 * ```
 * 00 ---- 04 ------ 06 ------------------ 20 ------ 22 ---- 24
 *  full night  |  dawn  |     full day     |  dusk  |  full night
 * ```
 *
 * The dark budget is unchanged at six Bestia-hours (two real ones), so nothing about how much of a session is
 * spent at night moved; only where it falls. [isNight] is the *full* night, which is what a nocturnal
 * creature keys off. [daylight] is the continuous version and is what anything drawing the world wants: a
 * boolean cannot express a sunset, and a renderer given one produces a light switch.
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

  /**
   * True during full night - hours `[NIGHT_START_HOUR, HOURS_PER_DAY)` and `[0, NIGHT_END_HOUR)`.
   *
   * The dark middle, deliberately, not "anything darker than noon". This is what the AI's activity cycle
   * keys off, and a diurnal creature should be asleep through the dark rather than through every hour whose
   * light is less than full - which under a twilight model would have it bedding down before sunset.
   */
  val isNight: Boolean get() = hour >= NIGHT_START_HOUR || hour < NIGHT_END_HOUR

  /** True whenever it is not [isNight]. Includes the twilight hours, which are lit. */
  val isDay: Boolean get() = !isNight

  /**
   * How much of the sun's light is up, in `[0, 1]`: `1.0` in full day, `0.0` in full night, and a smooth
   * ramp across dawn and dusk.
   *
   * **The reference implementation of the curve, which the client re-implements.** It has to, because
   * lighting is a per-frame quantity and the clock is an anchor sent once per connection - there is no
   * message to carry this. The boundaries go on the wire so the two agree by construction rather than by
   * both being edited; see `WorldInfoSMSG`.
   *
   * Smoothstepped rather than linear. A linear ramp has a corner at each end, and a corner in a light level
   * is visible as a moment where the sky stops changing - which reads as a hitch rather than as dusk ending.
   */
  val daylight: Double
    get() {
      val h = hour + minute / 60.0 + second / 3_600.0

      return when {
        h < NIGHT_END_HOUR -> 0.0
        h < DAWN_END_HOUR -> smoothstep((h - NIGHT_END_HOUR) / (DAWN_END_HOUR - NIGHT_END_HOUR))
        h < DUSK_START_HOUR -> 1.0
        h < NIGHT_START_HOUR -> 1.0 - smoothstep((h - DUSK_START_HOUR) / (NIGHT_START_HOUR - DUSK_START_HOUR))
        else -> 0.0
      }
    }

  /** Fraction of the current season/month elapsed, in `[0, 1)`. */
  val seasonProgress: Double
    get() = ((day - 1) * SECONDS_PER_DAY + hour * SECONDS_PER_HOUR + minute * 60 + second) /
      (DAYS_PER_MONTH * SECONDS_PER_DAY).toDouble()

  /** Fraction of the current Bestia year elapsed, in `[0, 1)`. */
  val yearProgress: Double
    get() = (((month - 1) * DAYS_PER_MONTH + (day - 1)) * SECONDS_PER_DAY +
      hour * SECONDS_PER_HOUR + minute * 60 + second) /
      (MONTHS_PER_YEAR * DAYS_PER_MONTH * SECONDS_PER_DAY).toDouble()

  /** Whole Bestia-days elapsed in the current year, `0..DAYS_PER_YEAR - 1`. */
  val dayOfYear: Int get() = (month - 1) * DAYS_PER_MONTH + (day - 1)

  /**
   * Bestia-days since the world began, fractional within the day.
   *
   * **This is the time coordinate the weather model takes**, and it is a `Double` in days rather than a
   * count of seconds for two reasons: the weather field's periods are expressed in days, so a caller never
   * has to divide; and it stays exact to well below a second for any world age a server will see, where a
   * `Float` would start losing minutes inside the first Bestia decade.
   */
  val absoluteDay: Double
    get() = (year - 1) * DAYS_PER_YEAR + dayOfYear + timeOfDay

  /**
   * Bestia-seconds since the world began. The exact companion to [absoluteDay], which is the same quantity
   * in days.
   *
   * Whole seconds and integer arithmetic, because this is what a caller *inverts*: [BestiaClock.jumpTo] has
   * to find the elapsed real time that produces a given date, and a date that does not round-trip through
   * that would land a `/date 02:00` on 01:59:59.
   */
  val absoluteSecond: Long
    get() = ((year - 1) * DAYS_PER_YEAR + dayOfYear) * SECONDS_PER_DAY +
      hour * SECONDS_PER_HOUR + minute * 60L + second

  companion object {
    /** Bestia time passes this many times faster than real-world time. */
    const val SPEED_FACTOR = 3.0

    const val HOURS_PER_DAY = 24
    const val DAYS_PER_MONTH = 30
    const val MONTHS_PER_YEAR = 4

    /**
     * The four hours the day is cut at. See the class KDoc for the picture.
     *
     * They must stay ordered `NIGHT_END < DAWN_END < DUSK_START < NIGHT_START < HOURS_PER_DAY`, which is
     * what lets [daylight] resolve them with a single ordered `when` and no wrap-around arithmetic. Only
     * the *full night* wraps midnight, and it wraps because it is the two open ends of that ordering.
     *
     * Full night is six Bestia-hours - 2 real-world hours * [SPEED_FACTOR], which is the length the docs
     * give. Twilight is on top of that rather than carved out of it: a dusk that ate into the dark would
     * make the nights shorter than the design says they are.
     */
    const val NIGHT_END_HOUR = 4

    const val DAWN_END_HOUR = 6
    const val DUSK_START_HOUR = 20
    const val NIGHT_START_HOUR = 22

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

    /** Hermite ease over `[0, 1]`, clamped. The ramp shape [daylight] uses. */
    private fun smoothstep(t: Double): Double {
      val x = t.coerceIn(0.0, 1.0)

      return x * x * (3.0 - 2.0 * x)
    }
  }
}
