package net.bestia.zone.ecs2.scenario

import net.bestia.zone.ecs2.EntityId
import net.bestia.zone.ecs2.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Demonstrates that running independent systems in parallel yields exactly the
 * same simulation result as running them sequentially — the safety guarantee
 * behind the scheduler's conflict-aware waves.
 */
class ParallelDeterminismTest {

  private fun runSimulation(parallel: Boolean): Map<EntityId, Pair<Float, Float>> {
    val world = World(parallelSystems = parallel)
    // deterministic registration order: wander decides velocity, movement integrates
    world.addSystems(listOf(WanderSystem(), MovementSystem(), HealthRegenSystem()))

    val ids = (1..64).map { world.create(it.toLong()) }
    ids.forEach { id ->
      world.add(id, Position())
      world.add(id, Velocity())
      world.add(id, Wander())
      world.add(id, Health(30))
    }

    repeat(200) { world.tick(0.05f) }

    return ids.associateWith { id ->
      val p = world.get<Position>(id)!!
      p.x to p.y
    }
  }

  @Test
  fun `parallel execution matches sequential execution bit for bit`() {
    val sequential = runSimulation(parallel = false)
    val parallel = runSimulation(parallel = true)
    assertEquals(sequential, parallel)
  }
}
