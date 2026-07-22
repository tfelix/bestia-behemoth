package net.bestia.zone.environment.time

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class BestiaDateTimeTest {

  @Test
  fun `zero elapsed time is the very start of year 1`() {
    val time = BestiaDateTime.since(Duration.ZERO)

    assertEquals(1L, time.year)
    assertEquals(1, time.month)
    assertEquals(1, time.day)
    assertEquals(0, time.hour)
    assertEquals(0, time.minute)
    assertEquals(0, time.second)
  }

  @Test
  fun `one real hour passes as three Bestia hours`() {
    val time = BestiaDateTime.since(Duration.ofHours(1))

    assertEquals(3, time.hour)
    assertEquals(0, time.minute)
  }

  @Test
  fun `real minutes and seconds scale by the speed factor too`() {
    val time = BestiaDateTime.since(Duration.ofMinutes(20))

    // 20 real minutes * 3 = 60 Bestia minutes = 1 Bestia hour
    assertEquals(1, time.hour)
    assertEquals(0, time.minute)
  }

  @Test
  fun `a full Bestia day passes after 8 real hours`() {
    val time = BestiaDateTime.since(Duration.ofHours(8))

    assertEquals(2, time.day)
    assertEquals(0, time.hour)
  }

  @Test
  fun `a full Bestia month passes after 10 real days`() {
    val time = BestiaDateTime.since(Duration.ofDays(10))

    assertEquals(1L, time.year)
    assertEquals(2, time.month)
    assertEquals(1, time.day)
  }

  @Test
  fun `a full Bestia year passes after 40 real days`() {
    val time = BestiaDateTime.since(Duration.ofDays(40))

    assertEquals(2L, time.year)
    assertEquals(1, time.month)
    assertEquals(1, time.day)
  }

  @Test
  fun `negative elapsed duration clamps to the world's creation instant`() {
    val time = BestiaDateTime.since(Duration.ofHours(-5))

    assertEquals(1L, time.year)
    assertEquals(1, time.month)
    assertEquals(1, time.day)
    assertEquals(0, time.hour)
  }

  @Test
  fun `custom speed factor of 1 behaves like real time`() {
    val time = BestiaDateTime.since(Duration.ofHours(5), speedFactor = 1.0)

    assertEquals(5, time.hour)
  }

  @Test
  fun `timeOfDay is 0 at midnight and 0,5 at noon`() {
    val midnight = BestiaDateTime.since(Duration.ZERO)
    val noon = BestiaDateTime.since(Duration.ofHours(4)) // 4 real hours * 3 = 12 Bestia hours

    assertEquals(0.0, midnight.timeOfDay)
    assertEquals(0.5, noon.timeOfDay)
  }

  @Test
  fun `isNight is true for the first six Bestia hours and false after`() {
    val justBeforeDawn = BestiaDateTime.since(Duration.ofMinutes(119)) // ~5.95 Bestia hours
    val dawn = BestiaDateTime.since(Duration.ofHours(2)) // exactly 6 Bestia hours

    assertTrue(justBeforeDawn.isNight)
    assertFalse(justBeforeDawn.isDay)
    assertFalse(dawn.isNight)
    assertTrue(dawn.isDay)
  }

  @Test
  fun `season follows the documented summer, winter, fall, spring order`() {
    assertEquals(Season.SUMMER, BestiaDateTime.since(Duration.ZERO).season)
    assertEquals(Season.WINTER, BestiaDateTime.since(Duration.ofDays(10)).season)
    assertEquals(Season.FALL, BestiaDateTime.since(Duration.ofDays(20)).season)
    assertEquals(Season.SPRING, BestiaDateTime.since(Duration.ofDays(30)).season)
  }

  @Test
  fun `seasonProgress reaches 0,5 exactly halfway through a month`() {
    val halfway = BestiaDateTime.since(Duration.ofDays(5)) // half of the 10 real day month

    assertEquals(0.5, halfway.seasonProgress, 1e-9)
  }

  @Test
  fun `yearProgress reaches 0,5 exactly halfway through a year`() {
    val halfway = BestiaDateTime.since(Duration.ofDays(20)) // half of the 40 real day year

    assertEquals(0.5, halfway.yearProgress, 1e-9)
  }

  @Test
  fun `rejects an out-of-range month`() {
    assertThrows<IllegalArgumentException> {
      BestiaDateTime(year = 1, month = 5, day = 1, hour = 0, minute = 0, second = 0)
    }
  }

  @Test
  fun `rejects an out-of-range hour`() {
    assertThrows<IllegalArgumentException> {
      BestiaDateTime(year = 1, month = 1, day = 1, hour = 24, minute = 0, second = 0)
    }
  }

  @Test
  fun `rejects a year below 1`() {
    assertThrows<IllegalArgumentException> {
      BestiaDateTime(year = 0, month = 1, day = 1, hour = 0, minute = 0, second = 0)
    }
  }
}
