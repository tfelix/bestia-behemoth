package net.bestia.zone.ecs.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private class CompA : Component
private class CompB : Component
private class CompC : Component

private class CountingSystem(
  override val schedule: Schedule,
  override val reads: Set<kotlin.reflect.KClass<out Component>> = emptySet(),
  override val writes: Set<kotlin.reflect.KClass<out Component>> = emptySet(),
) : System {
  var runs = 0

  /** Every delta this system was handed, summed - the quantity a decay or countdown integrates over. */
  var totalDelta = 0f

  override fun update(world: World, deltaTime: Float) {
    runs++
    totalDelta += deltaTime
  }
}

class SystemSchedulerTest {

  @Test
  fun `EveryTick runs each tick, EveryTicks and EverySeconds respect cadence`() {
    val everyTick = CountingSystem(Schedule.EveryTick)
    val everyThird = CountingSystem(Schedule.EveryTicks(3))
    val everySecond = CountingSystem(Schedule.EverySeconds(1.0f))
    val world = testWorld(systems = listOf(everyTick, everyThird, everySecond))

    // 6 ticks of 0.5s each = 3.0s total
    repeat(6) { world.tick(0.5f) }

    assertEquals(6, everyTick.runs)
    assertEquals(2, everyThird.runs, "every 3rd of 6 ticks")
    assertEquals(3, everySecond.runs, "every 1.0s over 3.0s")
  }

  @Test
  fun `the deltas handed to a paced system add up to the time that actually passed`() {
    // 0.1 does not divide 0.25, so every firing carries a remainder into the next period - which is exactly
    // the case that used to be counted twice. A system integrating over these deltas (a countdown, a decay,
    // an unload delay) would have run 10% fast, and only for schedules whose period is not a tick multiple.
    val paced = CountingSystem(Schedule.EverySeconds(0.25f))
    val everyTick = CountingSystem(Schedule.EveryTick)
    val world = testWorld(systems = listOf(paced, everyTick))

    repeat(100) { world.tick(0.1f) }

    assertEquals(10.0f, everyTick.totalDelta, 0.01f)
    // Tolerance of one period, because whatever has accumulated since the last firing has not been
    // reported yet; the point is that it cannot exceed the elapsed time.
    assertEquals(10.0f, paced.totalDelta, 0.25f)
  }

  @Test
  fun `a tick longer than the period does not leave a system firing on every tick afterwards`() {
    // A lag spike. Subtracting a single period would leave the accumulator still over the threshold, so the
    // entry would consider itself behind on every subsequent tick and never catch up - a paced system
    // silently promoted to EveryTick for the rest of the process.
    val paced = CountingSystem(Schedule.EverySeconds(0.25f))
    val world = testWorld(systems = listOf(paced))

    world.tick(2.0f)
    assertEquals(1, paced.runs, "the overdue tick itself should fire once")

    repeat(20) { world.tick(0.05f) }

    assertEquals(5, paced.runs, "1.0s at 0.25s cadence is four more firings, not one per tick")
  }

  @Test
  fun `non-conflicting systems share a wave, conflicting ones are serialised`() {
    // writes A, writes B, reads A (conflicts with writer of A)
    val world = testWorld(
      systems = listOf(
        CountingSystem(Schedule.EveryTick, writes = setOf(CompA::class)),
        CountingSystem(Schedule.EveryTick, writes = setOf(CompB::class)),
        CountingSystem(Schedule.EveryTick, reads = setOf(CompA::class)),
      )
    )
    // wave 0: {writes A, writes B} (disjoint) ; wave 1: {reads A} (conflicts with writes A)
    assertEquals(2, world.waveCount)
  }

  @Test
  fun `fully independent systems collapse into a single wave`() {
    val world = testWorld(
      systems = listOf(
        CountingSystem(Schedule.EveryTick, writes = setOf(CompA::class)),
        CountingSystem(Schedule.EveryTick, writes = setOf(CompB::class)),
        CountingSystem(Schedule.EveryTick, writes = setOf(CompC::class)),
      )
    )
    assertEquals(1, world.waveCount)
  }

  @Test
  fun `parallel execution produces the same counts as sequential`() {
    val a = CountingSystem(Schedule.EveryTick, writes = setOf(CompA::class))
    val b = CountingSystem(Schedule.EveryTick, writes = setOf(CompB::class))
    val world = testWorld(parallelSystems = true, systems = listOf(a, b))

    repeat(10) { world.tick(0.1f) }

    assertEquals(10, a.runs)
    assertEquals(10, b.runs)
    assertTrue(world.waveCount == 1)
  }
}
