package net.bestia.zone.world.time

import net.bestia.zone.world.time.BestiaDateTime.Companion.DAWN_END_HOUR
import net.bestia.zone.world.time.BestiaDateTime.Companion.DUSK_START_HOUR
import net.bestia.zone.world.time.BestiaDateTime.Companion.NIGHT_END_HOUR
import net.bestia.zone.world.time.BestiaDateTime.Companion.NIGHT_START_HOUR

/** The four parts of a Bestia day, cut at the same hours the sun is. */
enum class DayPart {
  DAWN,
  DAY,
  DUSK,
  NIGHT;

  companion object {
    fun ofHour(hour: Int): DayPart = when {
      hour < NIGHT_END_HOUR -> NIGHT
      hour < DAWN_END_HOUR -> DAWN
      hour < DUSK_START_HOUR -> DAY
      hour < NIGHT_START_HOUR -> DUSK
      else -> NIGHT
    }
  }
}
