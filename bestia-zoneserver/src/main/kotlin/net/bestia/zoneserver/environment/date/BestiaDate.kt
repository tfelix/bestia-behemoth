package net.bestia.zoneserver.environment.date

import java.time.Clock
import java.time.Duration
import java.time.Instant
import kotlin.math.ceil

class BestiaDate(
    startTime: Instant,
    clock: Clock? = null
) {
  private val sinceStart = Duration.between(
      startTime,
      clock?.let { Instant.now(it) } ?: Instant.now()
  )

  /**
   * Returns the progress in percentage of the month. Starting at the first
   * day of the month with 0.0 and then going up to 1.0 for the last day.
   *
   * @return The current progress of the month.
   */
  val monthProgress: Float
    get() = dayOfMonth / MONTH_DAYS.toFloat()

  val hourOfDay: Int
    get() = (sinceStart.toHours() % DAY_HOURS).toInt()

  val minutesOfHour: Int
    get() = (sinceStart.toMinutes() % HOUR_MINUTES).toInt()

  val dayOfMonth: Int
    get() = (sinceStart.toDays() % (MONTH_DAYS + 1)).toInt()

  val monthOfYear: Int
    get() = ((sinceStart.toMinutes() % BMINUTES_OF_YEAR) / BMINUTES_OF_MONTH).toInt() + 1

  val year: Int
    get() = (sinceStart.toMinutes() / BMINUTES_OF_YEAR).toInt() + 1

  /**
   * Returns the current season.
   *
   * @return The current season in the bestia system.
   */
  val season: Season
    get() {
      val seasonNumber = monthOfYear / MONTHS_PER_SEASON
      return Season.values()[seasonNumber]
    }

  override fun toString(): String {
    return "BestiaDate[$year-$monthOfYear-$dayOfMonth $hourOfDay:$minutesOfHour]"
  }

  companion object {
    private const val HOUR_MINUTES = 60
    private const val DAY_HOURS = 16
    private const val MONTH_DAYS = 30
    private const val YEAR_MONTHS = 8
    private const val MONTHS_PER_SEASON = YEAR_MONTHS / 4

    private const val BMINUTES_OF_DAY = HOUR_MINUTES * DAY_HOURS
    private const val BMINUTES_OF_MONTH = MONTH_DAYS * BMINUTES_OF_DAY
    private const val BMINUTES_OF_YEAR = YEAR_MONTHS * BMINUTES_OF_MONTH
  }
}
