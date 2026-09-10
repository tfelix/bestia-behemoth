package net.bestia.zone.ai.core.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The midnight wrap, which is the only thing in here that is easy to get wrong and the reason the type
 * exists at all rather than a pair of Ints being passed about.
 */
class HourWindowTest {

  @Test
  fun `an ordinary daytime span covers its own hours and no others`() {
    val shift = HourWindow(6, 18)

    assertTrue(shift.covers(6), "the span starts at its first hour")
    assertTrue(shift.covers(17))
    assertFalse(shift.covers(18), "and ends before its last, or two adjoining shifts would share an hour")
    assertFalse(shift.covers(5))
    assertFalse(shift.covers(0))
  }

  @Test
  fun `a span that wraps midnight covers both sides of it`() {
    val night = HourWindow(22, 6)

    assertTrue(night.covers(22))
    assertTrue(night.covers(23))
    assertTrue(night.covers(0), "midnight is inside a night, which is the whole difficulty")
    assertTrue(night.covers(5))
    assertFalse(night.covers(6))
    assertFalse(night.covers(12))
  }

  @Test
  fun `overlapping is symmetric and sees across midnight`() {
    val night = HourWindow(22, 6)
    val earlyShift = HourWindow(5, 13)

    assertTrue(night.overlaps(earlyShift), "05:00 is in both")
    assertTrue(earlyShift.overlaps(night))
    assertFalse(night.overlaps(HourWindow(6, 22)))
  }

  @Test
  fun `a span with no hours in it is refused rather than silently empty`() {
    // `from == to` reads as either "no hours" or "every hour" depending on which side of the wrap test it
    // falls, and an occupation that meant one and got the other would be unwatchable.
    assertFailsWith<IllegalArgumentException> { HourWindow(9, 9) }
    assertFailsWith<IllegalArgumentException> { HourWindow(24, 6) }
    assertFailsWith<IllegalArgumentException> { HourWindow(6, -1) }
  }

  @Test
  fun `it prints as hours`() {
    assertEquals("06:00-18:00", HourWindow(6, 18).toString())
  }
}
