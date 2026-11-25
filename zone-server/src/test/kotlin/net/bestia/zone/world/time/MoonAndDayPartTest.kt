package net.bestia.zone.world.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MoonAndDayPartTest {

  @Test
  fun `the day is cut where the sun is`() {
    assertEquals(DayPart.NIGHT, BestiaDateTime(1, 1, 1, 3, 59, 0).dayPart)
    assertEquals(DayPart.DAWN, BestiaDateTime(1, 1, 1, 4, 0, 0).dayPart)
    assertEquals(DayPart.DAY, BestiaDateTime(1, 1, 1, 12, 0, 0).dayPart)
    assertEquals(DayPart.DUSK, BestiaDateTime(1, 1, 1, 21, 0, 0).dayPart)
    assertEquals(DayPart.NIGHT, BestiaDateTime(1, 1, 1, 22, 0, 0).dayPart)
  }

  @Test
  fun `the moon is new at the start of each month and full halfway`() {
    assertEquals(MoonPhase.NEW, BestiaDateTime(1, 1, 1, 0, 0, 0).moonPhase)
    assertEquals(MoonPhase.WAXING, BestiaDateTime(1, 1, 8, 0, 0, 0).moonPhase)
    assertEquals(MoonPhase.FULL, BestiaDateTime(1, 1, 16, 0, 0, 0).moonPhase)
    assertEquals(MoonPhase.WANING, BestiaDateTime(1, 1, 23, 0, 0, 0).moonPhase)
    assertEquals(MoonPhase.NEW, BestiaDateTime(1, 2, 1, 0, 0, 0).moonPhase)
  }
}
