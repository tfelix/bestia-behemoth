package net.bestia.zone.environment.time

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class BestiaClockTest {

  private val epoch: Instant = Instant.parse("2026-01-01T00:00:00Z")

  @Test
  fun `now reflects elapsed real time since the configured world epoch`() {
    val fixedNow = epoch.plus(Duration.ofHours(8)) // exactly one Bestia day later
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch),
      clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    val time = clock.now()

    assertEquals(2, time.day)
    assertEquals(0, time.hour)
  }

  @Test
  fun `now honors a custom speed factor`() {
    val fixedNow = epoch.plus(Duration.ofHours(1))
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch, speedFactor = 1.0),
      clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    assertEquals(1, clock.now().hour)
  }

  @Test
  fun `with no configured world epoch, the clock treats its own construction as world creation`() {
    val constructionInstant = Instant.parse("2026-06-15T12:00:00Z")
    val underlyingClock = Clock.fixed(constructionInstant, ZoneOffset.UTC)

    val clock = BestiaClock(config = BestiaTimeConfig(worldEpoch = null), clock = underlyingClock)

    val time = clock.now()

    assertEquals(1L, time.year)
    assertEquals(1, time.month)
    assertEquals(1, time.day)
    assertEquals(0, time.hour)
  }
}
