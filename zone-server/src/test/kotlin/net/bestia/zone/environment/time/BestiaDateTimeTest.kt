package net.bestia.zone.environment.time

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Duration

class BestiaDateTimeTest {

  /** 120 Bestia days at 3x real time and 8 real hours a day. Spelled out so the arithmetic is visible. */
  private val REAL_DAYS_PER_YEAR = 40L

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
  fun `full night straddles midnight`() {
    assertTrue(at(0).isNight)
    assertTrue(at(23).isNight)
    assertTrue(at(BestiaDateTime.NIGHT_START_HOUR).isNight)
    assertTrue(at(BestiaDateTime.NIGHT_END_HOUR - 1).isNight)

    // The hours either side of it are twilight, which is lit - so not night.
    assertFalse(at(BestiaDateTime.NIGHT_END_HOUR).isNight)
    assertFalse(at(BestiaDateTime.NIGHT_START_HOUR - 1).isNight)
    assertTrue(at(BestiaDateTime.NIGHT_START_HOUR - 1).isDay)
    assertFalse(at(12).isNight)
  }

  @Test
  fun `night is still six Bestia-hours long`() {
    val darkHours = (0 until BestiaDateTime.HOURS_PER_DAY).count { at(it).isNight }

    // Twilight sits on top of the dark rather than eating into it: the length the docs give is 2 real hours
    // of night, and moving where night falls must not have quietly shortened it.
    assertEquals(6, darkHours)
  }

  @Test
  fun `daylight is full by day, zero at night and ramps between`() {
    assertEquals(1.0, at(12).daylight)
    assertEquals(1.0, at(BestiaDateTime.DAWN_END_HOUR).daylight)
    assertEquals(1.0, at(BestiaDateTime.DUSK_START_HOUR - 1).daylight)

    assertEquals(0.0, at(0).daylight)
    assertEquals(0.0, at(BestiaDateTime.NIGHT_START_HOUR).daylight)
    assertEquals(0.0, at(BestiaDateTime.NIGHT_END_HOUR - 1).daylight)

    // The ramps hit halfway at their midpoints - 05:00 and 21:00, which is where the sun crosses the horizon.
    assertEquals(0.5, at(5).daylight, 1e-9)
    assertEquals(0.5, at(21).daylight, 1e-9)

    // And they are monotone, which is the property a light level actually has to have: a sky that brightens
    // then dims again inside one dawn reads as a bug however smooth each half is.
    val dawn = (0..119).map { minutes ->
      at(BestiaDateTime.NIGHT_END_HOUR + minutes / 60, minute = minutes % 60).daylight
    }
    assertEquals(dawn.sorted(), dawn)
  }

  /**
   * The two answer the same question for different callers - the AI asks "is it dark", the renderer asks
   * "how dark" - so a creature asleep in a lit world is what a disagreement between them looks like.
   *
   * Stated as an implication rather than an equality, because they legitimately differ at exactly one
   * instant: at 04:00:00 sharp, full night has ended and the dawn ramp has not yet risen off zero. Both
   * readings are right there, and a strict equality would only be pinning that tick.
   */
  @Test
  fun `night is always fully dark`() {
    (0 until BestiaDateTime.HOURS_PER_DAY).forEach { hour ->
      if (at(hour).isNight) {
        assertEquals(0.0, at(hour).daylight, "hour $hour is night but has light in it")
      }
    }

    // And the light does start moving the moment night is over, rather than at some later hour.
    assertTrue(at(BestiaDateTime.NIGHT_END_HOUR, minute = 1).daylight > 0.0)
  }

  private fun at(hour: Int, minute: Int = 0) =
    BestiaDateTime(year = 1, month = 1, day = 1, hour = hour, minute = minute, second = 0)

  @Test
  fun `season runs spring, summer, fall, winter through the year`() {
    assertEquals(Season.SPRING, BestiaDateTime.since(Duration.ZERO).season)
    assertEquals(Season.SUMMER, BestiaDateTime.since(Duration.ofDays(10)).season)
    assertEquals(Season.FALL, BestiaDateTime.since(Duration.ofDays(20)).season)
    assertEquals(Season.WINTER, BestiaDateTime.since(Duration.ofDays(30)).season)
  }

  @Test
  fun `dayOfYear counts whole days from the start of the year and wraps at the new year`() {
    assertEquals(0, BestiaDateTime.since(Duration.ZERO).dayOfYear)
    assertEquals(30, BestiaDateTime.since(Duration.ofDays(10)).dayOfYear) // one month in

    // One real second before the year turns: the last Bestia day of the year, then back to zero.
    assertEquals(119, BestiaDateTime.since(Duration.ofDays(REAL_DAYS_PER_YEAR).minusSeconds(1)).dayOfYear)
    assertEquals(0, BestiaDateTime.since(Duration.ofDays(REAL_DAYS_PER_YEAR)).dayOfYear)
  }

  @Test
  fun `absoluteDay keeps counting across the new year`() {
    assertEquals(0.0, BestiaDateTime.since(Duration.ZERO).absoluteDay, 1e-9)
    assertEquals(30.0, BestiaDateTime.since(Duration.ofDays(10)).absoluteDay, 1e-9)
    assertEquals(120.0, BestiaDateTime.since(Duration.ofDays(REAL_DAYS_PER_YEAR)).absoluteDay, 1e-9)
    assertEquals(240.0, BestiaDateTime.since(Duration.ofDays(2 * REAL_DAYS_PER_YEAR)).absoluteDay, 1e-9)
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

  /**
   * [BestiaDateTime.absoluteSecond] is what `WorldInfoSMSG` puts on the wire and what `BestiaClock.jumpTo`
   * inverts, so it has to agree with [BestiaDateTime.since] in both directions. A discrepancy of one second
   * here is a `/date 02:00` that lands on 01:59:59.
   */
  @Test
  fun `absoluteSecond round-trips through since`() {
    val time = BestiaDateTime(year = 3, month = 2, day = 17, hour = 2, minute = 30, second = 45)

    // Divided by the speed factor because `since` takes *real* elapsed time.
    val elapsed = Duration.ofSeconds((time.absoluteSecond / BestiaDateTime.SPEED_FACTOR).toLong())

    assertEquals(time, BestiaDateTime.since(elapsed))
  }

  @Test
  fun `absoluteSecond and absoluteDay are the same quantity`() {
    val time = BestiaDateTime(year = 2, month = 3, day = 8, hour = 13, minute = 5, second = 20)

    assertEquals(
      time.absoluteDay * BestiaDateTime.HOURS_PER_DAY * 3_600.0,
      time.absoluteSecond.toDouble(),
      1e-6
    )
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
