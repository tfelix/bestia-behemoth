package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.worldgen.voxel.CarveBrush
import net.bestia.zone.world.WorldService
import org.awaitility.Awaitility.await
import java.time.Duration
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Chunks are generated and encoded on workers; the tick only hands out what is ready. */
class ChunkServiceAsyncEncodeTest {

  /** Holds submitted work until the test runs it, so "still on its way" is a state the test can stand in. */
  private val jobs = ArrayDeque<Runnable>()
  private val workers = ChunkWorkers(Executor { jobs.addLast(it) })

  private val worldService: WorldService = mockk {
    every { generated } returns world
    every { config } returns world.config
    every { isLoaded } returns true
  }

  private fun service(settings: ChunkStreamConfig = ChunkStreamConfig()): ChunkService {
    return ChunkService(worldService, settings, workers).also { it.beginTick() }
  }

  private fun runWorkers() {
    while (jobs.isNotEmpty()) jobs.removeFirst().run()
    workers.drain()
  }

  @Test
  fun `a chunk is encoded once however often it is asked for on its way`() {
    val service = service()

    assertNull(service.readyDataMessage(CHUNK))
    assertNull(service.readyDataMessage(CHUNK))

    assertEquals(1, jobs.size)
  }

  @Test
  fun `a finished chunk is served, and is the same payload an inline encode makes`() {
    val service = service()
    service.readyDataMessage(CHUNK)

    runWorkers()

    val ready = assertNotNull(service.readyDataMessage(CHUNK))
    val inline = ChunkService(worldService, ChunkStreamConfig()).dataMessageFor(CHUNK)
    assertContentEquals(inline.payload, ready.payload)
    assertEquals(inline.baseHash, ready.baseHash)
  }

  @Test
  fun `a carve while the chunk is on its way is not overwritten by the stale result`() {
    val service = service()
    val (chunk, brush) = carvable()
    service.readyDataMessage(chunk)

    service.carve(brush)
    runWorkers()

    val ready = assertNotNull(service.readyDataMessage(chunk), "an edited chunk is encoded at once")
    assertEquals(1, ready.revision)
  }

  @Test
  fun `the per-tick budget caps how many chunks start`() {
    val service = service(ChunkStreamConfig(encodesPerTick = 2))

    service.readyDataMessage(ChunkPos(0, 0, 0))
    service.readyDataMessage(ChunkPos(1, 0, 0))
    service.readyDataMessage(ChunkPos(2, 0, 0))
    assertEquals(2, jobs.size)

    service.beginTick()
    service.readyDataMessage(ChunkPos(2, 0, 0))
    assertEquals(3, jobs.size)
  }

  @Test
  fun `real worker threads deliver on the next drain`() {
    val pooled = ChunkWorkers(ChunkStreamConfig(encodeWorkers = 2))
    val service = ChunkService(worldService, ChunkStreamConfig(), pooled).also { it.beginTick() }

    try {
      await().atMost(Duration.ofSeconds(30)).until {
        pooled.drain()
        service.readyDataMessage(CHUNK) != null
      }
    } finally {
      pooled.shutdown()
    }
  }

  /** A chunk and a brush that is sure to remove rock in it: just under the generated ground. */
  private fun carvable(): Pair<ChunkPos, CarveBrush> {
    val size = config.chunkSize.toLong()
    val voxelX = 40L
    val voxelY = 40L
    val column = ChunkPos(Math.floorDiv(voxelX, size).toInt(), Math.floorDiv(voxelY, size).toInt(), 0)
    val elevation = world.columns.heights(column, 0)[Math.floorMod(voxelX, size).toInt(), Math.floorMod(voxelY, size).toInt()]
    val voxelZ = config.voxelZOf(elevation - 10.0)

    val chunk = ChunkPos(column.x, column.y, Math.floorDiv(voxelZ.toLong(), config.chunkHeight.toLong()).toInt())
    val brush = CarveBrush.sphere(voxelX + 0.5, voxelY + 0.5, voxelZ + 0.5, CarveBrush.MIN_RADIUS + 0.4)

    return chunk to brush
  }

  private companion object {
    val CHUNK = ChunkPos(1, 1, 0)

    // Shared, because generating a world per test method would dominate the run.
    val world = StandardWorld.build(WorldConfig(seed = 9002L, widthCells = 48, heightCells = 48))
    val config = world.config
  }
}
