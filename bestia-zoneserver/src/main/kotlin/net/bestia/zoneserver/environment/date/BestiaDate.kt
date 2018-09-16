package net.bestia.zoneserver.environment.date

import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class BestiaDate private constructor(
        private val startDate: LocalDateTime
) {

  /**
   * Returns the progress percentage of the day. Starting at 0 o'clock a the
   * night which is then 0 and 24 o'clock which is given as 1.0.
   *
   * @return The current progress of the day.
   */
  val dayProgress: Float
    get() {
      val now = LocalDateTime.now()
      return Duration.between(startDate, now).toMinutes() % MINUTES_OF_DAY / MINUTES_OF_DAY.toFloat()
    }

  /**
   * Returns the progress in percentage of the month. Starting at the first
   * day of the month with 0.0 and then going up to 1.0 for the last day.
   *
   * @return The current progress of the month.
   */
  val monthProgress: Float
    get() {
      val now = LocalDateTime.now()
      return Duration.between(startDate, now).toMinutes() % MINUTES_OF_MONTH / MINUTES_OF_MONTH.toFloat()
    }

  /**
   * The current progress of the year. Starting at the first day with 0.0 and
   * then going up to 1.0 for the last day of the year.
   *
   * @return The current progress of the year.
   */
  internal val yearProgress: Float
    get() {
      val now = LocalDateTime.now()
      return Duration.between(startDate, now).toMinutes() % MINUTES_OF_YEAR / MINUTES_OF_YEAR.toFloat()
    }

  /**
   * Returns the current hour of the day in the bestia time format. (24h)
   *
   * @return Current hour of the bestia world day.
   */
  val hours: Int
    get() = getHours(LocalDateTime.now())

  /**
   * Returns the current minute of the day in the bestia time format.
   *
   * @return Current minute of the bestia world day.
   */
  val minutes: Int
    get() = getMinutes(LocalDateTime.now())

  /**
   * Returns the current season.
   *
   * @return The current season in the bestia system.
   */
  val season: Season
    get() {
      val tempTime = LocalDateTime.from(startDate)
      val monthsPerSeason = YEAR_MONTHS / 4.0f
      val season = (tempTime.until(LocalDateTime.now(), ChronoUnit.MONTHS) % YEAR_MONTHS / monthsPerSeason).toInt()
      return Season.values()[season]
    }

  /**
   * Creates a new date object with the starting time set to now.
   */
  constructor() : this(LocalDateTime.now()) {
    // no op.
  }

  /**
   * Returns the current hour of the day in the bestia time format. (24h)
   *
   * @return Current hour of the bestia world day.
   */
  fun getHours(now: LocalDateTime): Int {
    return (Duration.between(startDate, now).toHours() % DAY_HOURS).toInt()
  }

  /**
   * Returns the current bestia minutes from the start time to the given time.
   *
   * @param now
   * @return Current minute of the bestia hour.
   */
  fun getMinutes(now: LocalDateTime): Int {
    val durationFromStart = Duration.between(startDate, now).toMinutes()
    return (durationFromStart % HOUR_MINUTES).toInt()
  }

  override fun toString(): String {
    return String.format("BestiaDate[%d:%d (%s)]", hours, minutes, season)
  }

  companion object {
    private const val HOUR_MINUTES = 60
    private const val DAY_HOURS = 16
    private const val MONTH_DAYS = 30
    private const val YEAR_MONTHS = 8

    private const val MINUTES_OF_DAY = HOUR_MINUTES * DAY_HOURS
    private const val MINUTES_OF_MONTH = MONTH_DAYS * MINUTES_OF_DAY
    private const val MINUTES_OF_YEAR = YEAR_MONTHS * MINUTES_OF_MONTH

    /**
     * Creates a bestia date from the given [java.util.Date] object which
     * is typically stored inside the database.
     *
     * @param date
     * @return
     */
    fun fromDate(date: Instant): BestiaDate {
      val time = date.atZone(ZoneId.of("UTC")).toLocalDateTime()

      return BestiaDate(time)
    }
  }
}
