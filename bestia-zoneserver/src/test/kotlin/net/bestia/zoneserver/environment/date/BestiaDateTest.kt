package net.bestia.zoneserver.environment.date

import org.junit.Assert
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class BestiaDateTest {

  @Test
  fun `monthProgress at startOfMonth is 0`() {
    val clock = Clock.fixed(Instant.parse("2019-01-01T00:00:00.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)
    Assert.assertEquals(0f, sut.monthProgress)
  }

  @Test
  fun `monthProgress at endOfMonth is 1`() {
    val clock = Clock.fixed(Instant.parse("2019-01-31T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    Assert.assertEquals(1f, sut.monthProgress)
  }

  @Test
  fun `year is 1 after 3 months`() {
    val clock = Clock.fixed(Instant.parse("2019-07-31T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    Assert.assertEquals(1, sut.year)
  }

  @Test
  fun `season is fall after 3 months`() {
    val clock = Clock.fixed(Instant.parse("2019-04-01T23:59:59.000Z"), ZoneId.of("UTC"))
    val sut = BestiaDate(Instant.parse("2019-01-01T00:00:00.000Z"), clock)

    Assert.assertEquals(Season.AUTUMN, sut.season)
  }
}