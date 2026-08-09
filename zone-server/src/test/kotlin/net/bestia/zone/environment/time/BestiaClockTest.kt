package net.bestia.zone.environment.time

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.world.PersistedWorld
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class BestiaClockTest {

  private val epoch: Instant = Instant.parse("2026-01-01T00:00:00Z")

  /** A world whose row claims it was created at [createdAt]. Nothing else about it is read. */
  private fun worldCreatedAt(createdAt: Instant): WorldService = mockk {
    every { record } returns mockk<PersistedWorld> { every { this@mockk.createdAt } returns createdAt }
  }

  @Test
  fun `now reflects elapsed real time since the configured world epoch`() {
    val fixedNow = epoch.plus(Duration.ofHours(8)) // exactly one Bestia day later
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch),
      worldService = worldCreatedAt(Instant.EPOCH),
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
      worldService = worldCreatedAt(Instant.EPOCH),
      clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    assertEquals(1, clock.now().hour)
  }

  @Test
  fun `with no configured world epoch, the clock counts from when the world row was written`() {
    val fixedNow = epoch.plus(Duration.ofHours(8)) // one Bestia day after the world was created
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = null),
      worldService = worldCreatedAt(epoch),
      clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    val time = clock.now()

    assertEquals(1L, time.year)
    assertEquals(1, time.month)
    assertEquals(2, time.day)
    assertEquals(0, time.hour)
  }

  /**
   * The regression this anchor exists for.
   *
   * The clock used to fall back to `Instant.now()` at construction, so a restart reset the calendar to Year 1
   * Day 1 even though the world, the masters and their positions had all survived it - and with them the
   * weather, which is `f(seed, region, t)` over the same clock.
   */
  @Test
  fun `a restart does not rewind the calendar`() {
    val world = worldCreatedAt(epoch)

    // Forty real hours is five Bestia days - deliberately not a whole month, so landing back on day 1 can
    // only mean the epoch was rewound rather than the calendar having come round again.
    val muchLater = epoch.plus(Duration.ofHours(40))

    val beforeRestart = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = null),
      worldService = world,
      clock = Clock.fixed(muchLater, ZoneOffset.UTC),
    ).now()

    // A second instance is exactly what a reboot produces: same world row, brand new bean.
    val afterRestart = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = null),
      worldService = world,
      clock = Clock.fixed(muchLater, ZoneOffset.UTC),
    ).now()

    assertEquals(beforeRestart, afterRestart)
    assertNotEquals(1, afterRestart.day)
  }
}
