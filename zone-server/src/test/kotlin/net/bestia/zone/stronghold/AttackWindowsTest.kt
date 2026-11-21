package net.bestia.zone.stronghold

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AttackWindowsTest {

  @Test
  fun `no windows means a Stronghold can always be attacked`() {
    assertTrue(AttackWindows(emptyList(), ZoneId.of("UTC")).isOpen(Instant.parse("2026-11-18T03:00:00Z")))
  }

  @Test
  fun `a window is open from its start up to its end, on its day, in its zone`() {
    val windows = AttackWindows(listOf(AttackWindow.parse("saturday 18:00-22:00")), ZoneId.of("Europe/Berlin"))

    // 2026-11-21 is a Saturday; Berlin is UTC+1 in November.
    assertFalse(windows.isOpen(Instant.parse("2026-11-21T16:59:00Z")))
    assertTrue(windows.isOpen(Instant.parse("2026-11-21T17:00:00Z")))
    assertTrue(windows.isOpen(Instant.parse("2026-11-21T20:59:00Z")))
    assertFalse(windows.isOpen(Instant.parse("2026-11-21T21:00:00Z")))
    assertFalse(windows.isOpen(Instant.parse("2026-11-22T18:00:00Z")))
  }

  @Test
  fun `a window is written as a day and two times`() {
    assertEquals(AttackWindow(DayOfWeek.MONDAY, LocalTime.of(8, 0), LocalTime.of(9, 30)), AttackWindow.parse("MONDAY 8:00 - 9:30"))
    assertThrows<IllegalArgumentException> { AttackWindow.parse("Saturday evening") }
    assertThrows<IllegalArgumentException> { AttackWindow.parse("SUNDAY 22:00-02:00") }
  }

  @Test
  fun `a bad window stops the boot`() {
    assertThrows<IllegalArgumentException> { StrongholdConfig(attackWindows = listOf("FUNDAY 18:00-22:00")) }
  }
}
