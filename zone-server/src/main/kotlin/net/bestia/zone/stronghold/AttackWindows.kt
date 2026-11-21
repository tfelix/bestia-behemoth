package net.bestia.zone.stronghold

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** One weekly span in which Strongholds can be attacked, such as `SATURDAY 18:00-22:00`. */
data class AttackWindow(val day: DayOfWeek, val start: LocalTime, val end: LocalTime) {

  init {
    require(end.isAfter(start)) { "an attack window ends after it starts; split one that crosses midnight in two" }
  }

  companion object {
    private val FORMAT = Regex("""(\w+)\s+(\d{1,2}:\d{2})\s*-\s*(\d{1,2}:\d{2})""")

    fun parse(text: String): AttackWindow {
      val match = FORMAT.matchEntire(text.trim())
        ?: throw IllegalArgumentException("attack window '$text' is not like 'SATURDAY 18:00-22:00'")
      val (day, start, end) = match.destructured

      return AttackWindow(DayOfWeek.valueOf(day.uppercase()), timeOf(start), timeOf(end))
    }

    private fun timeOf(text: String): LocalTime {
      return LocalTime.parse(text.padStart(5, '0'))
    }
  }
}

/** When Strongholds can be attacked. No windows at all means always. */
class AttackWindows(private val windows: List<AttackWindow>, private val zone: ZoneId) {

  fun isOpen(at: Instant): Boolean {
    if (windows.isEmpty()) return true

    val local = at.atZone(zone)
    val time = local.toLocalTime()

    return windows.any { it.day == local.dayOfWeek && !time.isBefore(it.start) && time.isBefore(it.end) }
  }

  companion object {
    fun of(config: StrongholdConfig): AttackWindows {
      return AttackWindows(config.attackWindows.map(AttackWindow::parse), ZoneId.of(config.attackWindowZone))
    }
  }
}
