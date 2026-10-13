package net.bestia.zone.ground

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.bestia.zone.ground.persistence.GroundLayerMark
import net.bestia.zone.ground.persistence.GroundLayerMarkRepository

/**
 * The wear store: what it records, what it refuses to record, and what it deliberately does not write.
 *
 * The last two are design properties rather than optimisations. Wear nobody holds is dropped, or a shard
 * accumulates paths in ground no player has ever seen; and a footfall does not write, or a walking creature
 * becomes a database round trip per metre. Tests are the only thing that keeps either.
 */
class GroundWearRegistryTest {

  private val chunkSize = 32
  private val thisShape = 11L
  private val thisPipeline = 22L
  private val config = GroundWearConfig()

  private lateinit var repository: GroundLayerMarkRepository
  /** What the registry's clock reads, so a test can move time on after building it. */
  private var clockSecond = 0L

  private fun registry(
    rows: List<GroundLayerMark> = emptyList(),
    now: Long = 0,
  ): GroundWearRegistry {
    clockSecond = now

    repository = mockk(relaxed = true) {
      every { findByIdColumnKey(any()) } answers { rows.filter { it.id.columnKey == firstArg<Long>() } }
      // `save` returns the generic `S`, and a relaxed mock answers that with a bare Object the caller cannot
      // cast. Echo the argument back, which is what a real repository does.
      every { save(any<GroundLayerMark>()) } answers { firstArg() }
    }

    val worldService = mockk<WorldService> {
      every { config } returns mockk { every { this@mockk.chunkSize } returns this@GroundWearRegistryTest.chunkSize }
      every { record } returns mockk {
        every { shapeVersion } returns thisShape
        every { pipelineVersion } returns thisPipeline
      }
    }

    // Inline, so the write assertions below are about a `save` that actually happened.
    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    val clock = mockk<BestiaClock> {
      every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } }
    }

    return GroundWearRegistry(repository, executor, worldService, config, clock)
  }

  private fun row(columnKey: Long, level: Int, shape: Long = thisShape, pipeline: Long = thisPipeline):
      GroundLayerMark {
    val levels = ColumnLevels(chunkSize)
    levels.add(0, 0, level)

    return GroundLayerMark(
      GroundLayerMark.Key(columnKey, GroundLayer.WORN.wireId), levels.toBytes(), 0, shape, pipeline
    )
  }

  @Test
  fun `ground nobody is holding is not worn down`() {
    val registry = registry()

    // Ambient creatures spawn further out than the chunk stream reaches, so this really happens. Wear nobody
    // can be told about is wear that should not be stored.
    assertFalse(registry.wear(voxelX = 5, voxelY = 5, amount = 100, nowSecond = 0))
    assertEquals(0, registry.markedColumns)
  }

  @Test
  fun `a tracked column records what walks on it`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))

    registry.wear(5, 5, 100, 0)

    assertEquals(100, registry.columnAt(ColumnKey.of(0, 0))!!.levels[5, 5])
  }

  @Test
  fun `only a crossing of the visible threshold is worth announcing`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))

    assertFalse(registry.wear(5, 5, config.visibleThreshold - 1, 0), "still below notice")
    assertTrue(registry.wear(5, 5, 1, 0), "this is the step that makes a path")
  }

  @Test
  fun `a footfall writes nothing`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))

    registry.wear(5, 5, 100, 0)

    // The whole point of coalescing. A creature walking is thousands of these a second across a shard.
    verify(exactly = 0) { repository.save(any<GroundLayerMark>()) }
  }

  @Test
  fun `releasing a column writes it out`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))
    registry.wear(5, 5, 100, 0)

    registry.release(ColumnKey.of(0, 0))

    verify { repository.save(any<GroundLayerMark>()) }
    assertEquals(0, registry.markedColumns)
  }

  @Test
  fun `a column that has grown over is deleted rather than stored empty`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))

    registry.release(ColumnKey.of(0, 0))

    verify { repository.deleteByIdColumnKeyAndIdLayerId(ColumnKey.of(0, 0), GroundLayer.WORN.wireId) }
    verify(exactly = 0) { repository.save(any<GroundLayerMark>()) }
  }

  @Test
  fun `stored wear comes back when somebody returns to it`() {
    val key = ColumnKey.of(0, 0)
    val registry = registry(rows = listOf(row(key, 200)))

    registry.track(key)
    registry.drainLoaded(0)

    assertEquals(200, registry.columnAt(key)!!.levels[0, 0])
  }

  @Test
  fun `a column is usable before its stored wear has arrived`() {
    val key = ColumnKey.of(0, 0)
    val registry = registry(rows = listOf(row(key, 200)))

    registry.track(key)
    // The read is in flight. A footfall now must not be blocked on it, and must not be lost by it.
    registry.wear(1, 0, 100, 0)
    registry.drainLoaded(0)

    assertEquals(200, registry.columnAt(key)!!.levels[0, 0], "what was stored")
    assertEquals(100, registry.columnAt(key)!!.levels[1, 0], "and what happened while it was being read")
  }

  @Test
  fun `a row from another world is discarded rather than drawn on whatever is there now`() {
    val key = ColumnKey.of(0, 0)
    val registry = registry(rows = listOf(row(key, 200, shape = 99L)))

    registry.track(key)
    registry.drainLoaded(0)

    assertEquals(0, registry.columnAt(key)!!.levels[0, 0])
    verify { repository.delete(any<GroundLayerMark>()) }
  }

  /**
   * Below the threshold nothing is drawn, but a client that lays wear itself has to start from the same levels
   * as the server or it will cross the threshold a footfall early or late - so faint wandering is sent too.
   */
  @Test
  fun `a column holding only faint wandering is sent at its exact levels`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))
    registry.wear(5, 5, config.visibleThreshold - 1, 0)

    val entry = registry.entryAt(ColumnKey.of(0, 0))

    assertEquals(GroundLayerEncoding.LEVELS_SPARSE_V1, entry.encoding)
    assertContentEquals(
      byteArrayOf((5 * 32 + 5 and 0xFF).toByte(), 0x00, (config.visibleThreshold - 1).toByte()),
      entry.cells
    )
  }

  @Test
  fun `a walked path is sent with the clock its levels have faded to`() {
    val registry = registry(now = 5_000)
    registry.track(ColumnKey.of(0, 0))
    registry.wear(5, 5, 255, 5_000)

    val entry = registry.entryAt(ColumnKey.of(0, 0))

    assertEquals(GroundLayer.WORN, entry.layer)
    assertEquals(ColumnLevels.SPARSE_BYTES_PER_CELL, entry.cells.size)
    assertEquals(5_000L, entry.lastDecayedSecond)
  }

  /** A client continues the fade from `lastDecayedSecond`, so levels that disagree with it fade the same time twice. */
  @Test
  fun `the levels are aged to now before they are sent`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))
    registry.wear(5, 5, 255, 0)

    clockSecond = config.fadeSeconds / 2

    val entry = registry.entryAt(ColumnKey.of(0, 0))

    assertEquals(128, entry.cells[2].toInt() and 0xFF)
    assertEquals(129_091L, entry.lastDecayedSecond)
  }

  @Test
  fun `an untracked column has nothing to send`() {
    assertTrue(registry().entryAt(ColumnKey.of(3, 4)).isEmpty)
  }

  @Test
  fun `a column that has faded away is an empty entry that retires the layer`() {
    val registry = registry()
    registry.track(ColumnKey.of(0, 0))
    registry.wear(5, 5, 100, 0)

    clockSecond = config.fadeSeconds

    val entry = registry.entryAt(ColumnKey.of(0, 0))

    assertTrue(entry.isEmpty)
    assertEquals(GroundLayerEncoding.LEVELS_SPARSE_V1, entry.encoding)
  }
}
