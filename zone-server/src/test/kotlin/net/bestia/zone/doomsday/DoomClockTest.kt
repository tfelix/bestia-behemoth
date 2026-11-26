package net.bestia.zone.doomsday

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DoomClockTest {

  private val clock = DoomClock(DoomsdayConfig())
  private val start = DoomClock.State(0.0, DoomStage.OMENS)

  @Test
  fun `left alone the clock reaches its end exactly at the end of the life span`() {
    var state = start
    var day = 0.0
    while (day < LIFE_SPAN - 1.0) {
      state = clock.advance(state, ONSET, UNFOLD, day, day + 0.25, pace = 1.0)
      day += 0.25
    }
    assertTrue(state.progress < 1.0, "not over a day early: ${state.progress}")

    state = clock.advance(state, ONSET, UNFOLD, day, LIFE_SPAN, pace = 1.0)
    assertEquals(1.0, state.progress, 1e-9)
    assertEquals(DoomStage.CATACLYSM, state.stage)
    assertEquals(DoomEnding.LOST, clock.ending(state, LIFE_SPAN, LIFE_SPAN))
  }

  @Test
  fun `the clock stands still before onset and counts only the days after it`() {
    val before = clock.advance(start, ONSET, UNFOLD, 0.0, ONSET, pace = 3.0)
    assertEquals(0.0, before.progress)

    val across = clock.advance(start, ONSET, UNFOLD, ONSET - 10.0, ONSET + 30.0, pace = 1.0)
    assertEquals(30.0 / UNFOLD, across.progress, 1e-9)
  }

  @Test
  fun `the pace follows the threat and stays within its bounds`() {
    assertEquals(1.0, clock.paceOf(pressure = 0.0, containment = 0.0))
    assertEquals(1.5, clock.paceOf(pressure = 0.7, containment = 0.2), 1e-9)
    assertEquals(3.0, clock.paceOf(pressure = 9.0, containment = 0.0))
    assertEquals(0.25, clock.paceOf(pressure = 0.0, containment = 9.0))
  }

  @Test
  fun `a deed pushes the clock but never past its ends`() {
    val pushed = clock.push(DoomClock.State(0.5, DoomStage.ESCALATION), -0.15)
    assertEquals(0.35, pushed.progress, 1e-9)
    assertEquals(DoomStage.ONSET, pushed.stage)

    assertEquals(0.0, clock.push(start, -1.0).progress)
    assertEquals(1.0, clock.push(start, 5.0).progress)
  }

  @Test
  fun `a stage changes only past its border, in both directions`() {
    assertEquals(DoomStage.OMENS, clock.set(start, 0.16).stage)
    val onset = clock.set(start, 0.17)
    assertEquals(DoomStage.ONSET, onset.stage)

    assertEquals(DoomStage.ONSET, clock.set(onset, 0.14).stage)
    assertEquals(DoomStage.OMENS, clock.set(onset, 0.12).stage)
  }

  @Test
  fun `a world held long enough is saved, even when its clock ran out the same day`() {
    val ongoing = DoomClock.State(0.8, DoomStage.ESCALATION)
    assertNull(clock.ending(ongoing, LIFE_SPAN * 1.49, LIFE_SPAN))
    assertEquals(DoomEnding.SAVED, clock.ending(ongoing, LIFE_SPAN * 1.5, LIFE_SPAN))
    assertEquals(DoomEnding.SAVED, clock.ending(DoomClock.State(1.0, DoomStage.CATACLYSM), LIFE_SPAN * 1.5, LIFE_SPAN))
  }

  private companion object {
    const val ONSET = 200.0
    const val UNFOLD = 300.0
    const val LIFE_SPAN = ONSET + UNFOLD
  }
}
