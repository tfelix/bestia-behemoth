package net.bestia.zone.world.fire

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The scorch store: what it remembers, what it refuses to load, and what it deliberately does not write.
 *
 * That last one is a design property rather than an optimisation - a scar shrinking has to cost nothing, or
 * regrowth becomes a write per column per minute forever - and a test is the only thing that keeps it.
 */
class ScorchRegistryTest {

  private val chunkSize = 32
  private val thisShape = 11L
  private val thisPipeline = 22L

  private lateinit var repository: ScorchRepository

  private fun registry(
    rows: List<ScorchMark> = emptyList(),
    shape: Long = thisShape,
    pipeline: Long = thisPipeline
  ): ScorchRegistry {
    repository = mockk(relaxed = true) {
      every { findAll() } returns rows
      // `save` returns the generic `S`, and a relaxed mock answers that with a bare Object the registry then
      // cannot cast. Echo the argument back, which is what a real repository does.
      every { save(any<ScorchMark>()) } answers { firstArg() }
    }

    val worldService = mockk<WorldService> {
      every { config } returns mockk { every { this@mockk.chunkSize } returns this@ScorchRegistryTest.chunkSize }
      every { record } returns mockk {
        every { shapeVersion } returns shape
        every { pipelineVersion } returns pipeline
      }
    }

    // Inline, so the durable-write assertions below are about a `save` that actually happened.
    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    return ScorchRegistry(repository, executor, worldService)
  }

  private fun cells(vararg at: Pair<Int, Int>): ColumnMask {
    val mask = ColumnMask(chunkSize)
    at.forEach { (x, y) -> mask.set(x, y) }
    return mask
  }

  private fun row(
    columnKey: Long,
    mask: ColumnMask = cells(1 to 1),
    shape: Long = thisShape,
    pipeline: Long = thisPipeline
  ) = ScorchMark(columnKey, mask.toBytes(), 1_000L, shape, pipeline)

  @Test
  fun `burning a column records it and writes one row`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(3, -4)

    assertTrue(sut.burn(key, cells(1 to 1, 2 to 2), burnedAtSecond = 500L))

    val scar = assertNotNull(sut.scarOf(key))
    assertEquals(2, scar.mask.count)
    assertEquals(500L, scar.burnedAtSecond)
    verify(exactly = 1) { repository.save(any<ScorchMark>()) }
  }

  @Test
  fun `burning cells already burnt is not a change and writes nothing further`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    sut.burn(key, cells(1 to 1), burnedAtSecond = 500L)

    assertTrue(!sut.burn(key, cells(1 to 1), burnedAtSecond = 500L), "a no-op burn reported a change")
    verify(exactly = 1) { repository.save(any<ScorchMark>()) }
  }

  /** Every column of one fire shares its ignition instant, which is what stops a scar healing in chunk-shaped steps. */
  @Test
  fun `a later fire over the same column resets its clock`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    sut.burn(key, cells(1 to 1), burnedAtSecond = 500L)

    val scar = assertNotNull(sut.scarOf(key))
    scar.rainMm = 30.0

    sut.burn(key, cells(5 to 5), burnedAtSecond = 900L)

    assertEquals(900L, scar.burnedAtSecond)
    assertEquals(0.0, scar.rainMm, "rain from the old window survived onto the new one")
    assertEquals(900L, scar.integratedThroughSecond)
  }

  @Test
  fun `an earlier fire does not pull a scar's clock backwards`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    sut.burn(key, cells(1 to 1), burnedAtSecond = 900L)
    sut.burn(key, cells(5 to 5), burnedAtSecond = 500L)

    assertEquals(900L, assertNotNull(sut.scarOf(key)).burnedAtSecond)
  }

  /**
   * **The property regrowth depends on.** A scar getting smaller must not touch the database, or healing costs
   * a write per scarred column per sweep for as long as anything is burnt.
   */
  @Test
  fun `a shrinking scar writes nothing`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    val wide = ColumnMask(chunkSize)
    for (y in 8..16) for (x in 8..16) wide.set(x, y)
    sut.burn(key, wide, burnedAtSecond = 500L)

    val scar = assertNotNull(sut.scarOf(key))
    val burnt = scar.mask.count

    scar.erodeTo(1)
    scar.erodeTo(2)
    scar.erodeTo(3)

    assertTrue(scar.visible.count < burnt, "erosion removed nothing, so this proves nothing")
    assertEquals(burnt, scar.mask.count, "the stored mask shrank; it must stay the original burn")
    verify(exactly = 1) { repository.save(any<ScorchMark>()) }
    verify(exactly = 0) { repository.deleteById(any()) }
  }

  /** Erosion is always against the stored mask, so re-eroding to the same depth cannot compound. */
  @Test
  fun `eroding to the same depth twice is idempotent`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    val wide = ColumnMask(chunkSize)
    for (y in 8..16) for (x in 8..16) wide.set(x, y)
    sut.burn(key, wide, burnedAtSecond = 500L)

    val scar = assertNotNull(sut.scarOf(key))
    scar.erodeTo(2)
    val once = scar.visible.count
    scar.erodeTo(2)

    assertEquals(once, scar.visible.count)
  }

  @Test
  fun `forgetting a healed scar removes it and deletes its row exactly once`() {
    val sut = registry()
    val key = ScorchRegistry.columnKeyOf(0, 0)
    sut.burn(key, cells(1 to 1), burnedAtSecond = 500L)

    sut.forget(key)
    sut.forget(key)

    assertNull(sut.scarOf(key))
    verify(exactly = 1) { repository.deleteById(key) }
  }

  @Test
  fun `a row belonging to this world is loaded`() {
    val key = ScorchRegistry.columnKeyOf(7, 7)
    val sut = registry(rows = listOf(row(key, cells(2 to 3))))

    sut.loadAll()

    val scar = assertNotNull(sut.scarOf(key))
    assertTrue(scar.mask[2, 3])
    verify(exactly = 0) { repository.deleteAll(any<List<ScorchMark>>()) }
  }

  @Test
  fun `a row from a reseeded world is discarded`() {
    val key = ScorchRegistry.columnKeyOf(7, 7)
    val sut = registry(rows = listOf(row(key, shape = thisShape - 1)))

    sut.loadAll()

    assertNull(sut.scarOf(key))
    verify { repository.deleteAll(any<List<ScorchMark>>()) }
  }

  @Test
  fun `a row from a rebuilt pipeline is discarded`() {
    val key = ScorchRegistry.columnKeyOf(7, 7)
    val sut = registry(rows = listOf(row(key, pipeline = thisPipeline - 1)))

    sut.loadAll()

    assertNull(sut.scarOf(key))
    verify { repository.deleteAll(any<List<ScorchMark>>()) }
  }

  /** A wrong-width mask that somehow matched both versions is skipped, not allowed to kill the boot. */
  @Test
  fun `a mask of the wrong width is skipped rather than thrown`() {
    val key = ScorchRegistry.columnKeyOf(7, 7)
    val wrong = ScorchMark(key, ByteArray(3), 1_000L, thisShape, thisPipeline)
    val sut = registry(rows = listOf(wrong))

    sut.loadAll()

    assertNull(sut.scarOf(key))
    assertEquals(0, sut.scarredColumns)
  }

  @Test
  fun `the stored row carries both of this world's versions`() {
    val sut = registry()
    val saved = slot<ScorchMark>()
    every { repository.save(capture(saved)) } answers { saved.captured }

    sut.burn(ScorchRegistry.columnKeyOf(1, 2), cells(1 to 1), burnedAtSecond = 500L)

    assertEquals(thisShape, saved.captured.worldShapeVersion)
    assertEquals(thisPipeline, saved.captured.pipelineVersion)
    assertEquals(500L, saved.captured.burnedAtSecond)
  }

  @Test
  fun `a column key round trips through its chunk coordinates`() {
    for (x in listOf(0, 1, -1, 5000, -5000)) {
      for (y in listOf(0, 1, -1, 5000, -5000)) {
        val key = ScorchRegistry.columnKeyOf(x, y)
        assertEquals(x, ScorchRegistry.chunkXOf(key), "x lost for ($x, $y)")
        assertEquals(y, ScorchRegistry.chunkYOf(key), "y lost for ($x, $y)")
      }
    }
  }
}
