package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

/** What the scheduler records about its phases and about each system's slowest update. */
class SchedulerStatsTest {

  private class SleepingSystem(override val phase: Phase, private val millis: Long) : System {
    override fun update(world: World, deltaTime: Float) {
      Thread.sleep(millis)
    }
  }

  private val world = testWorld()

  @Test
  fun `the wall time of a phase is what its systems took`() {
    val scheduler = SystemScheduler()
    scheduler.registerAll(listOf(SleepingSystem(Phase.AI, 5), SleepingSystem(Phase.COMBAT, 0)))

    scheduler.tick(world, 0.05f)

    assertTrue(scheduler.phaseNanos(Phase.AI) >= TimeUnit.MILLISECONDS.toNanos(5), "AI ran a 5 ms system")
    assertTrue(scheduler.phaseNanos(Phase.COMBAT) < scheduler.phaseNanos(Phase.AI))
    assertEquals(0L, scheduler.phaseNanos(Phase.UPKEEP), "no system of that phase ran")
  }

  @Test
  fun `the slowest update of a window is reported once the window has closed`() {
    // A window of no length closes at the start of every tick.
    val scheduler = SystemScheduler(maxWindowNanos = 0)
    scheduler.register(SleepingSystem(Phase.AI, 5))
    val stats = scheduler.stats().single()

    scheduler.tick(world, 0.05f)
    assertEquals(0L, stats.maxNanosLastWindow, "the first window had not closed when it was read")

    scheduler.tick(world, 0.05f)
    assertTrue(stats.maxNanosLastWindow >= TimeUnit.MILLISECONDS.toNanos(5), "the first tick's update is the max")
  }

  @Test
  fun `a window the system did not run in reports nothing`() {
    val scheduler = SystemScheduler(maxWindowNanos = 0)
    scheduler.register(object : System {
      override val phase = Phase.AI
      override val schedule = Schedule.EveryTicks(3)

      override fun update(world: World, deltaTime: Float) {
        Thread.sleep(2)
      }
    })
    val stats = scheduler.stats().single()

    repeat(3) { scheduler.tick(world, 0.05f) } // runs on the third
    assertEquals(1L, stats.runs)

    scheduler.tick(world, 0.05f) // closes the window the run was in
    assertTrue(stats.maxNanosLastWindow >= TimeUnit.MILLISECONDS.toNanos(2))

    scheduler.tick(world, 0.05f) // closes a window with no run in it
    assertEquals(0L, stats.maxNanosLastWindow)
  }
}
