package net.bestia.zone.ai.core.state

/**
 * A span of the world calendar's own 0..23 clock: from [fromHour] inclusive to [toHour] exclusive,
 * wrapping past midnight when the end is not after the start.
 *
 * Its whole reason for existing is that the wrap is easy to get wrong and is written once here. A shift, a
 * bedtime and the [UntilHour][net.bestia.zone.ai.bt.UntilHour] decorator that runs a behaviour for the
 * length of one are all the same question - "is the clock inside this span" - and answering it three times
 * is how a guard ends up going home at midnight.
 *
 * Written in hours and asked in minutes. Spans are authored in config files and there is no shift that
 * begins at twenty past; what needs the finer grain is the asking, because a town whose every shift ends
 * on the same tick empties in one step. See [coversMinute].
 */
data class HourWindow(val fromHour: Int, val toHour: Int) {

  init {
    require(fromHour in 0..23) { "fromHour must be an hour of the day, was $fromHour" }
    require(toHour in 0..23) { "toHour must be an hour of the day, was $toHour" }
    require(fromHour != toHour) { "a window from $fromHour to $toHour is either empty or the whole day" }
  }

  val wrapsMidnight: Boolean get() = toHour < fromHour

  val fromMinuteOfDay: Int get() = fromHour * MINUTES_PER_HOUR
  val toMinuteOfDay: Int get() = toHour * MINUTES_PER_HOUR

  /**
   * Whether [minuteOfDay] is inside the span for somebody whose own day sits [offsetMinutes] off the
   * stated hours.
   *
   * The offset moves the *person*, not the span, which is what keeps one number honest across a whole
   * day: a townsperson's shift and their bed are both read through it, so their evening is the length it
   * was written as and their post can never drift into their own bedtime. A guard's watch ends at exactly
   * the minute his night begins, so anything that moved the two ends independently would put him at his
   * post in bed.
   *
   * Zero for anybody who keeps the stated hours.
   */
  fun coversMinute(minuteOfDay: Int, offsetMinutes: Int): Boolean {
    val own = Math.floorMod(minuteOfDay - offsetMinutes, MINUTES_PER_DAY)

    return if (wrapsMidnight) {
      own >= fromMinuteOfDay || own < toMinuteOfDay
    } else {
      own in fromMinuteOfDay until toMinuteOfDay
    }
  }

  /**
   * Whether the two spans share an hour. Used to refuse an occupation that would work through its own bed.
   *
   * Two arcs of a circle, neither empty and neither the whole day, meet exactly when one contains the
   * other's start - so this is two tests rather than a sweep of the clock.
   */
  fun overlaps(other: HourWindow): Boolean {
    return coversMinute(other.fromMinuteOfDay, 0) || other.coversMinute(fromMinuteOfDay, 0)
  }

  override fun toString(): String {
    return "%02d:00-%02d:00".format(fromHour, toHour)
  }

  companion object {
    const val MINUTES_PER_HOUR = 60
    const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
  }
}
