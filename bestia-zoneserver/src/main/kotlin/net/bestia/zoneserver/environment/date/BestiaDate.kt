package net.bestia.zoneserver.environment.date

import java.time.Clock
import java.time.Instant

class BestiaDate(
    private val startTime: Instant,
    private val clock: Clock? = null
) {
  /**
   * Returns the progress in percentage of the month. Starting at the first
   * day of the month with 0.0 and then going up to 1.0 for the last day.
   *
   * @return The current progress of the month.
   */
  val monthProgress: Float
    get() = dayOfMonth.toFloat() * MINUTES_OF_DAY / MINUTES_OF_MONTH

  val hourOfDay: Int
    get() = 0

  val minutesOfHour: Int
    get() = (((minutesSinceStart() % MINUTES_OF_YEAR) % MINUTES_OF_DAY) % HOUR_MINUTES).toInt()

  val dayOfMonth: Int
    get() = ((minutesSinceStart() % MINUTES_OF_YEAR) / MINUTES_OF_MONTH).toInt()

  val monthOfYear: Int
    get() = ((minutesSinceStart() % MINUTES_OF_YEAR) / MINUTES_OF_MONTH).toInt()

  val year: Int
    get() = (minutesSinceStart() / MINUTES_OF_YEAR).toInt()

  /**
   * Returns the current season.
   *
   * @return The current season in the bestia system.
   */
  val season: Season
    get() {
      val seasonNumber = (monthOfYear / MONTHS_PER_SEASON).toInt()
      return Season.values()[seasonNumber]
    }

  private fun daysSinceStart(): Long  = minutesSinceStart() / MINUTES_OF_DAY

  private fun minutesSinceStart(): Long {
    val now = clock?.let { Instant.now(it) } ?: Instant.now()
    return (now.epochSecond - startTime.epochSecond) / 60
  }

  override fun toString(): String {
    return "BestiaDate[$year-$monthOfYear-$dayOfMonth $hourOfDay:$minutesOfHour]"
  }

  companion object {
    private const val HOUR_MINUTES = 60
    private const val DAY_HOURS = 16
    private const val MONTH_DAYS = 30
    private const val YEAR_MONTHS = 8
    private const val MONTHS_PER_SEASON = YEAR_MONTHS / 4.0f

    private const val MINUTES_OF_DAY = HOUR_MINUTES * DAY_HOURS
    private const val MINUTES_OF_MONTH = MONTH_DAYS * MINUTES_OF_DAY
    private const val MINUTES_OF_YEAR = YEAR_MONTHS * MINUTES_OF_MONTH
  }
}
