package net.bestia.zone.ecs

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class FixedStepClockTest {

  private val step = 50L

  private fun clock(): FixedStepClock {
    return FixedStepClock(stepNanos = step, maxCatchUpSteps = 3, startNanos = 0L)
  }

  @Test
  fun `one step is due per step length`() {
    val clock = clock()

    assertEquals(1, clock.dueSteps(0L))
    assertEquals(0, clock.dueSteps(49L))
    assertEquals(1, clock.dueSteps(50L))
    assertEquals(100L, clock.nextStepAt)
  }

  @Test
  fun `a late wake-up keeps the schedule instead of starting a new one`() {
    val clock = clock()
    clock.dueSteps(0L)

    assertEquals(1, clock.dueSteps(70L))
    assertEquals(100L, clock.nextStepAt, "the 20 late nanos are not added to the next step")
  }

  @Test
  fun `a short overrun is caught up`() {
    val clock = clock()
    clock.dueSteps(0L)

    assertEquals(2, clock.dueSteps(100L))
    assertEquals(0L, clock.droppedSteps)
  }

  @Test
  fun `a long pause runs at most the catch-up limit and drops the rest`() {
    val clock = clock()
    clock.dueSteps(0L)

    assertEquals(3, clock.dueSteps(500L))
    assertEquals(7L, clock.droppedSteps, "10 steps were owed, 3 ran")
    assertEquals(550L, clock.nextStepAt)
  }
}
