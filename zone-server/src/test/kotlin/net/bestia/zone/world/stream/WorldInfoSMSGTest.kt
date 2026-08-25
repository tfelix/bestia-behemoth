package net.bestia.zone.world.stream

import io.mockk.mockk
import net.bestia.zone.environment.time.BestiaDateTime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration

/**
 * The calendar half of the world info, which the client refuses as a whole if any part of it is zero.
 *
 * `WorldClock.Anchor` treats a zero `hours_per_day`, `days_per_month`, `months_per_year` or
 * `time_speed_factor` as "this server predates the world clock" and leaves the HUD clock hidden rather than
 * showing Year 1 Day 1. That is the right call, but it makes a dropped field invisible instead of loud - so
 * the wire values are pinned here.
 */
class WorldInfoSMSGTest {

  private fun infoAt(elapsed: Duration) = WorldInfoSMSG.of(
    record = mockk(relaxed = true),
    config = mockk(relaxed = true),
    viewRadiusChunks = 8,
    surfacePatchVersion = 0x5EED,
    patchRadiusChunks = 24,
    now = BestiaDateTime.since(elapsed),
    timeSpeedFactor = BestiaDateTime.SPEED_FACTOR
  ).toBnetEnvelope().worldInfo

  /**
   * The two fields a client's disk cache of coarse patches rests on.
   *
   * Dropping either is silent in the worst way: a zero version reads as a perfectly good cache key, so every
   * world a player visits would share one, and the second world they entered would be drawn with the first
   * one's terrain. The wire values are pinned here for the same reason the calendar's are.
   */
  @Test
  fun `the surface patch identity reaches the wire`() {
    val info = infoAt(Duration.ofHours(8))

    assertEquals(0x5EED, info.surfacePatchVersion)
    assertEquals(24, info.patchRadiusChunks)
  }

  @Test
  fun `the calendar reaches the wire with nothing zeroed`() {
    val info = infoAt(Duration.ofHours(8))

    // Exactly the four the client's guard checks.
    assertTrue(info.hoursPerDay > 0, "hours_per_day would make the client refuse the calendar")
    assertTrue(info.daysPerMonth > 0, "days_per_month would make the client refuse the calendar")
    assertTrue(info.monthsPerYear > 0, "months_per_year would make the client refuse the calendar")
    assertTrue(info.timeSpeedFactor > 0.0, "a zero speed factor is a stopped clock")

    assertEquals(BestiaDateTime.HOURS_PER_DAY, info.hoursPerDay)
    assertEquals(BestiaDateTime.DAYS_PER_MONTH, info.daysPerMonth)
    assertEquals(BestiaDateTime.MONTHS_PER_YEAR, info.monthsPerYear)
  }

  /**
   * The client draws the dawn and dusk ramps itself, from these, because light level is a per-frame quantity
   * and the clock is an anchor sent once per connection. A boundary dropped on the wire arrives as a zero,
   * and a zero here does not look like a missing field - it looks like a world where dawn is already over at
   * midnight, which is a lighting bug nobody would trace back to a message.
   */
  @Test
  fun `the day's four boundaries reach the wire in order`() {
    val info = infoAt(Duration.ofHours(8))

    assertEquals(BestiaDateTime.NIGHT_END_HOUR, info.nightEndHour)
    assertEquals(BestiaDateTime.DAWN_END_HOUR, info.dawnEndHour)
    assertEquals(BestiaDateTime.DUSK_START_HOUR, info.duskStartHour)
    assertEquals(BestiaDateTime.NIGHT_START_HOUR, info.nightStartHour)

    assertTrue(
      info.nightEndHour < info.dawnEndHour &&
          info.dawnEndHour < info.duskStartHour &&
          info.duskStartHour < info.nightStartHour &&
          info.nightStartHour < info.hoursPerDay,
      "the client resolves these with an ordered comparison and has no wrap-around case"
    )
  }

  /**
   * The anchor is elapsed Bestia-seconds, so a world eight real hours old is one Bestia day in.
   *
   * Zero is a legitimate value here - a world created this instant - which is why the client's guard does not
   * check it, and why it is worth checking that it actually moves.
   */
  @Test
  fun `the age advances with the world`() {
    val secondsPerBestiaDay = BestiaDateTime.HOURS_PER_DAY * 3_600.0

    assertEquals(0.0, infoAt(Duration.ZERO).worldAgeBestiaSeconds, secondsPerBestiaDay / 1_000)
    assertEquals(
      secondsPerBestiaDay,
      infoAt(Duration.ofHours(8)).worldAgeBestiaSeconds,
      secondsPerBestiaDay / 1_000,
      "eight real hours at speed factor 3 is one Bestia day"
    )
  }
}
