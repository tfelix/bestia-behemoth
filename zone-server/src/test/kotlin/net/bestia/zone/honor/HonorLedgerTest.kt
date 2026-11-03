package net.bestia.zone.honor

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HonorLedgerTest {

  private val layer = HonorLayer(start = 20, cap = 100, recoveryPerDay = 1, grace = Duration.ofDays(1))
  private val town = layer.copy(start = 10, permanentBelow = -100)

  @Test
  fun `a loss starts healing once a full day passed without another`() {
    val ledger = HonorLedger().apply(-10, T0, layer)

    assertEquals(10, ledger.valueAt(T0.plus(Duration.ofHours(23)), layer))
    assertEquals(10, ledger.valueAt(T0.plus(Duration.ofDays(1)), layer))
    assertEquals(13, ledger.valueAt(T0.plus(Duration.ofDays(4)), layer))
    assertEquals(20, ledger.valueAt(T0.plus(Duration.ofDays(30)), layer))
  }

  @Test
  fun `a new loss restarts the grace period`() {
    val ledger = HonorLedger()
      .apply(-10, T0, layer)
      .apply(-5, T0.plus(Duration.ofDays(3)), layer)

    // Two days healed before the second loss, none since.
    assertEquals(7, ledger.valueAt(T0.plus(Duration.ofDays(4)), layer))
  }

  @Test
  fun `gains beyond the cap are not banked`() {
    val ledger = HonorLedger().apply(500, T0, layer)

    assertEquals(100, ledger.valueAt(T0, layer))
    assertEquals(50, ledger.apply(-50, T0, layer).valueAt(T0, layer))
  }

  @Test
  fun `a town that fell below its threshold never forgets`() {
    val ledger = HonorLedger().apply(-120, T0, town)

    assertTrue(ledger.permanent)
    assertEquals(-110, ledger.valueAt(T0.plus(Duration.ofDays(365)), town))
  }

  private companion object {
    val T0: Instant = Instant.parse("2026-11-03T12:00:00Z")
  }
}
