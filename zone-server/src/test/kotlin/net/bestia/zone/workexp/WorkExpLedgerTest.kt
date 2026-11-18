package net.bestia.zone.workexp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class WorkExpLedgerTest {

  private val now = 1_800_000_000_000L
  private val bank = 3600

  @Test
  fun `a master who never worked has a full bank`() {
    val sut = WorkExpLedger()

    assertEquals(3600.0, sut.draw(now, 10_000.0, bank), 0.001)
  }

  @Test
  fun `effort is paid in full while the bank has it`() {
    val sut = WorkExpLedger()

    assertEquals(1920.0, sut.draw(now, 1920.0, bank), 0.001)
    assertEquals(1680.0, sut.draw(now, 1920.0, bank), 0.001)
    assertEquals(0.0, sut.draw(now, 1920.0, bank), 0.001)
  }

  @Test
  fun `the bank refills one second per second`() {
    val sut = WorkExpLedger()
    sut.draw(now, 10_000.0, bank)

    assertEquals(60.0, sut.draw(now + 60_000, 10_000.0, bank), 0.001)
  }

  @Test
  fun `time spent away banks no more than the bank holds`() {
    val sut = WorkExpLedger()
    sut.draw(now, 10_000.0, bank)

    assertEquals(3600.0, sut.draw(now + 24 * 3600 * 1000L, 10_000.0, bank), 0.001)
  }

  /** The reason the cursor lives on the master row: a relogin must not hand out a fresh bank. */
  @Test
  fun `a ledger restored from its saved cursor pays what the original would have`() {
    val original = WorkExpLedger()
    original.draw(now, 3000.0, bank)

    val restored = WorkExpLedger(original.paidUntilMs)

    assertEquals(original.draw(now, 3000.0, bank), restored.draw(now, 3000.0, bank), 0.001)
  }
}
