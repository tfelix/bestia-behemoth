package net.bestia.zoneserver.environment.date

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class BestiaDateTest {

  @Test
  fun `monthProgress at startOfMonth is 0`() {
    val clock = Clock.fixed(Instant.parse("2019-01-01T00:00:00.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)
    assertEquals(0f, sut.monthProgress)
  }

  @Test
  fun `monthProgress at endOfMonth is 1`() {
    val clock = Clock.fixed(Instant.parse("2019-01-31T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    assertEquals(1f, sut.monthProgress)
  }

  @Test
  fun `monthProgress at start of next month is 0`() {
    val clock = Clock.fixed(Instant.parse("2019-02-01T00:00:01.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    assertEquals(0f, sut.monthProgress)
  }

  @Test
  fun `year is 1 after 3 months`() {
    val clock = Clock.fixed(Instant.parse("2019-03-31T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    assertEquals(1, sut.year)
  }

  @Test
  fun `year is 2 after 9 months`() {
    val clock = Clock.fixed(Instant.parse("2019-10-10T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    assertEquals(2, sut.year)
  }

  @Test
  fun `season is fall after 3 months`() {
    val clock = Clock.fixed(Instant.parse("2019-04-01T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    assertEquals(Season.AUTUMN, sut.season)
  }
}