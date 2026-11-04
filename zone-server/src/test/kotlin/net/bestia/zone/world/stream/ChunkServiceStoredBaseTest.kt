package net.bestia.zone.world.stream

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.core.WorldConfig
import net.bestia.worldgen.pipeline.StandardWorld
import net.bestia.zone.world.WorldService
import org.springframework.util.unit.DataSize
import java.util.concurrent.Executor
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The revision-0 payload is a chunk's base, so a base that fell out of the hot cache is decoded from it rather
 * than generated again, and the wire serves it without a worker.
 */
class ChunkServiceStoredBaseTest {

  /** Holds submitted work until the test runs it. */
  private val jobs = ArrayDeque<Runnable>()
  private val workers = ChunkWorkers(Executor { jobs.addLast(it) })
  private val meters = SimpleMeterRegistry()

  private val worldService: WorldService = mockk {
    every { generated } returns world
    every { config } returns world.config
    every { isLoaded } returns true
  }

  /** A hot cache of one chunk, so reading a second chunk pushes the first one out of it. */
  private fun service(encodedCacheSize: DataSize = DataSize.ofMegabytes(64)): ChunkService {
    val settings = ChunkStreamConfig(hotChunkCapacity = 1, encodedCacheSize = encodedCacheSize)
    return ChunkService(worldService, settings, workers, ChunkStreamMetrics(meters)).also { it.beginTick() }
  }

  private fun runWorkers() {
    while (jobs.isNotEmpty()) jobs.removeFirst().run()
    workers.drain()
  }

  /** Base chunk reads from [source], as the meters have them once the next tick starts. */
  private fun lookups(service: ChunkService, source: String): Double {
    service.beginTick()
    return meters.get("zone.chunk.base.lookups").tag("source", source).counter().count()
  }

  @Test
  fun `a chunk pushed out of the hot cache is decoded from its stored payload`() {
    val service = service()
    val first = service.base(A)
    service.base(B)

    val again = service.base(A)

    assertEquals(1.0, lookups(service, "stored"))
    assertEquals(2.0, lookups(service, "generated"))
    assertContentEquals(first.blocks, again.blocks)
    assertContentEquals(first.occupancy, again.occupancy)
  }

  @Test
  fun `a base the tick generated is on the wire at once, without a worker`() {
    val service = service()
    service.base(A)

    val ready = assertNotNull(service.readyDataMessage(A))

    assertTrue(jobs.isEmpty(), "nothing was left to encode")
    val inline = ChunkService(worldService, ChunkStreamConfig()).dataMessageFor(A)
    assertContentEquals(inline.payload, ready.payload)
    assertEquals(inline.baseHash, ready.baseHash)
  }

  @Test
  fun `a base a worker generated is stored, so the tick does not generate it again`() {
    val service = service()
    service.readyDataMessage(A)
    runWorkers()
    service.base(B)

    service.base(A)

    assertEquals(1.0, lookups(service, "stored"))
    assertEquals(2.0, lookups(service, "generated"), "A on the worker, B on the tick")
  }

  @Test
  fun `the cache stays inside its size and drops the payload read longest ago`() {
    // Each of the three fits alone, all three together do not.
    val size = listOf(A, B, C).sumOf { costOf(it) } - 1
    val service = service(encodedCacheSize = DataSize.ofBytes(size))
    service.base(A)
    service.base(B)
    service.base(C)

    service.base(A)

    assertEquals(4.0, lookups(service, "generated"), "A was dropped, so it had to be generated again")
    assertTrue(meters.get("zone.chunk.encoded.size").gauge().value() <= size)
    assertTrue(meters.get("zone.chunk.encoded.evictions").functionCounter().count() >= 1.0)
  }

  /** What the encoded cache charges for [chunk]'s base payload. */
  private fun costOf(chunk: ChunkPos): Long {
    val payload = ChunkService(worldService, ChunkStreamConfig()).dataMessageFor(chunk).payload
    return payload.size + EncodedChunkCache.ENTRY_OVERHEAD_BYTES
  }

  private companion object {
    val A = ChunkPos(1, 1, 0)
    val B = ChunkPos(2, 1, 0)
    val C = ChunkPos(3, 1, 0)

    // Shared, because generating a world per test method would dominate the run.
    val world = StandardWorld.build(WorldConfig(seed = 9002L, widthCells = 48, heightCells = 48))
  }
}
