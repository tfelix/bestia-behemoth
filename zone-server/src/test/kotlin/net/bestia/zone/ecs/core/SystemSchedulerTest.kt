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
  override fun update(world: World, deltaTime: Float) {
    runs++
  }
}

class SystemSchedulerTest {

  @Test
  fun `EveryTick runs each tick, EveryTicks and EverySeconds respect cadence`() {
    val world = World()
    val everyTick = CountingSystem(Schedule.EveryTick)
    val everyThird = CountingSystem(Schedule.EveryTicks(3))
    val everySecond = CountingSystem(Schedule.EverySeconds(1.0f))
    world.addSystems(listOf(everyTick, everyThird, everySecond))

    // 6 ticks of 0.5s each = 3.0s total
    repeat(6) { world.tick(0.5f) }

    assertEquals(6, everyTick.runs)
    assertEquals(2, everyThird.runs, "every 3rd of 6 ticks")
    assertEquals(3, everySecond.runs, "every 1.0s over 3.0s")
  }

  @Test
  fun `non-conflicting systems share a wave, conflicting ones are serialised`() {
    val world = World()
    // writes A, writes B, reads A (conflicts with writer of A)
    world.addSystems(
      listOf(
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
    val world = World()
    world.addSystems(
      listOf(
        CountingSystem(Schedule.EveryTick, writes = setOf(CompA::class)),
        CountingSystem(Schedule.EveryTick, writes = setOf(CompB::class)),
        CountingSystem(Schedule.EveryTick, writes = setOf(CompC::class)),
      )
    )
    assertEquals(1, world.waveCount)
  }

  @Test
  fun `parallel execution produces the same counts as sequential`() {
    val world = World(parallelSystems = true)
    val a = CountingSystem(Schedule.EveryTick, writes = setOf(CompA::class))
    val b = CountingSystem(Schedule.EveryTick, writes = setOf(CompB::class))
    world.addSystems(listOf(a, b))

    repeat(10) { world.tick(0.1f) }

    assertEquals(10, a.runs)
    assertEquals(10, b.runs)
    assertTrue(world.waveCount == 1)
  }
}
