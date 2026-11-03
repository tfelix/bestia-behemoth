package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.world.WorldService
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The derived rebuilds' time budget holds whether a build runs on the tick or is handed to a worker that runs it
 * at once - which is what [ChunkWorkers] does with no pool, as in the test profile. Before, only builds the tick
 * knew it did itself were timed, so without workers every rebuild the count allowed ran on the tick: seconds per
 * step in a scenario world, long enough for every lease to time out.
 */
class ChunkServiceDerivedBudgetTest {

  private val worldService: WorldService = mockk {
    every { generated } returns world
    every { config } returns world.config
    every { isLoaded } returns true
  }

  @Test
  fun `without workers the time budget stops the rebuilds after the first`() {
    val settings = ChunkStreamConfig(encodeWorkers = 0, derivedRebuildsPerTick = 8, derivedRebuildMillisPerTick = 1)
    val service = ChunkService(worldService, settings, ChunkWorkers(pool = null))
    CHUNKS.forEach { service.derived().track(it) }

    // A fresh chunk is generated before its tile is built, which takes far longer than a millisecond.
    assertEquals(1, service.rebuildDerived())
    assertEquals(CHUNKS.size - 1, service.derived().pendingRebuilds)
  }

  @Test
  fun `with workers a handoff costs next to nothing, so the count is what stops them`() {
    val jobs = ArrayDeque<Runnable>()
    // The production budget: eight handoffs fit in it many times over, where a single inline build does not.
    val settings = ChunkStreamConfig(derivedRebuildsPerTick = 8, derivedRebuildMillisPerTick = 5)
    val service = ChunkService(worldService, settings, ChunkWorkers { jobs.addLast(it) })

    // The first handoff pays for the mocked world service and a cold JIT, which alone can exceed a millisecond.
    service.derived().track(WARM_UP)
    service.rebuildDerived()
    jobs.clear()

    CHUNKS.forEach { service.derived().track(it) }

    assertEquals(8, service.rebuildDerived())
    assertEquals(8, jobs.size)
  }

  private companion object {
    val CHUNKS = (0 until 10).map { ChunkPos(it % 5, it / 5, 0) }
    val WARM_UP = ChunkPos(9, 9, 0)

    // Shared, because generating a world per test method would dominate the run.
    val world = StandardWorld.build(WorldConfig(seed = 9002L, widthCells = 48, heightCells = 48))
  }
}
