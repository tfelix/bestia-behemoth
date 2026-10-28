package net.bestia.zone.economy.shop

import net.bestia.zone.ai.core.state.HourWindow

/**
 * When one merchant stands behind their counter: their shift, moved by their own day offset - the same
 * pair `TownsfolkRoster` uses to put them at their post, so the counter is never open with nobody behind it.
 */
data class ShopHours(val shift: HourWindow?, val dayOffsetMinutes: Int) {

  /** A merchant with no stated shift keeps no hours, so their counter never closes. */
  fun isOpenAt(minuteOfDay: Int): Boolean {
    return shift?.coversMinute(minuteOfDay, dayOffsetMinutes) ?: true
  }

  val opensAtMinute: Int?
    get() = shift?.let { onClock(it.fromMinuteOfDay) }

  val closesAtMinute: Int?
    get() = shift?.let { onClock(it.toMinuteOfDay) }

  private fun onClock(statedMinute: Int): Int {
    return Math.floorMod(statedMinute + dayOffsetMinutes, HourWindow.MINUTES_PER_DAY)
  }

  companion object {
    val ALWAYS_OPEN = ShopHours(shift = null, dayOffsetMinutes = 0)
  }
}
