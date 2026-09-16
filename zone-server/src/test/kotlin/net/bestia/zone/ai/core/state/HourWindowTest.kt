package net.bestia.zone.ai.core.state

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The midnight wrap, which is the only thing in here that is easy to get wrong and the reason the type
 * exists at all rather than a pair of Ints being passed about - and the per-person offset, which is the
 * second thing that is easy to get wrong for exactly the same reason.
 */
class HourWindowTest {

  @Test
  fun `an ordinary daytime span covers its own hours and no others`() {
    val shift = HourWindow(6, 18)

    assertTrue(shift.coversMinute(at(6), ON_TIME), "the span starts at its first hour")
    assertTrue(shift.coversMinute(at(17), ON_TIME))
    assertFalse(shift.coversMinute(at(18), ON_TIME), "and ends before its last, or two shifts share an hour")
    assertFalse(shift.coversMinute(at(5), ON_TIME))
    assertFalse(shift.coversMinute(at(0), ON_TIME))
  }

  @Test
  fun `a span that wraps midnight covers both sides of it`() {
    val night = HourWindow(22, 6)

    assertTrue(night.coversMinute(at(22), ON_TIME))
    assertTrue(night.coversMinute(at(23), ON_TIME))
    assertTrue(night.coversMinute(at(0), ON_TIME), "midnight is inside a night, which is the whole difficulty")
    assertTrue(night.coversMinute(at(5), ON_TIME))
    assertFalse(night.coversMinute(at(6), ON_TIME))
    assertFalse(night.coversMinute(at(12), ON_TIME))
  }

  @Test
  fun `a span is asked to the minute, not to the hour`() {
    val shift = HourWindow(7, 17)

    assertTrue(shift.coversMinute(at(16, 59), ON_TIME))
    assertFalse(shift.coversMinute(at(17, 1), ON_TIME))
  }

  @Test
  fun `an offset moves the person, so the span keeps its length`() {
    val shift = HourWindow(7, 17)

    // Half an hour late to the post and half an hour late leaving it. Ten hours either way.
    assertFalse(shift.coversMinute(at(7), LATE))
    assertTrue(shift.coversMinute(at(7, 31), LATE))
    assertTrue(shift.coversMinute(at(17, 29), LATE), "they are still at work")
    assertFalse(shift.coversMinute(at(17, 31), LATE))
  }

  @Test
  fun `an offset wraps rather than falling off either end of the day`() {
    val night = HourWindow(22, 6)

    // Early to bed, from the far side of midnight. The subtraction goes negative and has to come back round.
    assertTrue(night.coversMinute(at(21, 31), EARLY))
    assertFalse(night.coversMinute(at(5, 31), EARLY), "they were up half an hour ago")
    assertTrue(night.coversMinute(at(0), EARLY))
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

  private fun at(hour: Int, minute: Int = 0): Int {
    return hour * HourWindow.MINUTES_PER_HOUR + minute
  }

  private companion object {
    const val ON_TIME = 0
    const val LATE = 30
    const val EARLY = -30
  }
}
