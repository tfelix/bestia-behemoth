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

  @Test
  fun `jumpTo lands on the requested date exactly`() {
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch),
      worldService = worldCreatedAt(Instant.EPOCH),
      clock = Clock.fixed(epoch.plus(Duration.ofHours(8)), ZoneOffset.UTC),
    )

    // 02:00 rather than a round hour count away: the point is that the *stated* time is what comes back,
    // whatever elapsed real time would otherwise have produced.
    val target = BestiaDateTime(year = 3, month = 2, day = 17, hour = 2, minute = 30, second = 0)

    assertEquals(target, clock.jumpTo(target))
    assertEquals(target, clock.now())
    assertTrue(clock.now().isNight)
    assertTrue(clock.isShifted)
  }

  /**
   * A jump sets the clock running from the new time; it does not stop it.
   *
   * The whole reason to move the calendar by hand is to watch something that only happens at a particular
   * hour, and half of those things are transitions - so a jump that froze the clock at the moment before one
   * would be the least useful possible version of this.
   */
  @Test
  fun `the clock keeps running after a jump`() {
    var instant = epoch.plus(Duration.ofHours(8))
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch),
      worldService = worldCreatedAt(Instant.EPOCH),
      // Reads the var on every call, which Clock.fixed cannot do - this is a clock that can be advanced.
      clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId) = this
        override fun instant() = instant
      },
    )

    clock.jumpTo(BestiaDateTime(year = 1, month = 1, day = 5, hour = 5, minute = 0, second = 0))

    // One real hour is three Bestia hours at the default speed factor, which crosses out of night.
    instant = instant.plus(Duration.ofHours(1))

    val after = clock.now()

    assertEquals(8, after.hour)
    assertEquals(5, after.day)
    assertFalse(after.isNight)
  }

  @Test
  fun `resetToRealTime puts the calendar back where the world's age puts it`() {
    val fixedNow = epoch.plus(Duration.ofHours(8))
    val clock = BestiaClock(
      config = BestiaTimeConfig(worldEpoch = epoch),
      worldService = worldCreatedAt(Instant.EPOCH),
      clock = Clock.fixed(fixedNow, ZoneOffset.UTC),
    )

    val real = clock.now()
    clock.jumpTo(BestiaDateTime(year = 9, month = 4, day = 30, hour = 23, minute = 59, second = 0))

    assertEquals(real, clock.resetToRealTime())
    assertFalse(clock.isShifted)
  }
}
