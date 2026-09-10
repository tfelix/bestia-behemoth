package net.bestia.zone.ai.core.state

/**
 * A span of the world calendar's own 0..23 clock: from [fromHour] inclusive to [toHour] exclusive,
 * wrapping past midnight when the end is not after the start.
 *
 * Its whole reason for existing is that the wrap is easy to get wrong and is written once here. A shift, a
 * bedtime and the [UntilHour][net.bestia.zone.ai.bt.UntilHour] decorator that runs a behaviour for the
 * length of one are all the same question - "is the clock inside this span" - and answering it three times
 * is how a guard ends up going home at midnight.
 */
data class HourWindow(val fromHour: Int, val toHour: Int) {

  init {
    require(fromHour in 0..23) { "fromHour must be an hour of the day, was $fromHour" }
    require(toHour in 0..23) { "toHour must be an hour of the day, was $toHour" }
    require(fromHour != toHour) { "a window from $fromHour to $toHour is either empty or the whole day" }
  }

  val wrapsMidnight: Boolean get() = toHour < fromHour

  fun covers(hour: Int): Boolean {
    return if (wrapsMidnight) hour >= fromHour || hour < toHour else hour in fromHour until toHour
  }

  /** Whether the two spans share an hour. Used to refuse an occupation that would work through its own bed. */
  fun overlaps(other: HourWindow): Boolean {
    return (0..23).any { covers(it) && other.covers(it) }
  }

  override fun toString(): String = "%02d:00-%02d:00".format(fromHour, toHour)
}
