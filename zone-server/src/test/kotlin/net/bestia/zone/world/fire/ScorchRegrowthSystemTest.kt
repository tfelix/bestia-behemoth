package net.bestia.zone.world.fire

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.testWorld
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rain healing a scar, with the weather faked.
 *
 * `RainAccumulator` is stubbed rather than driven, because what this needs to pin is the *bookkeeping* - that
 * a wet region loses its scar, a dry one keeps it, and neither costs a write until the scar is gone. Whether
 * the integration itself counts millimetres correctly is a different question and belongs to a test that has a
 * generated world to ask.
 */
class ScorchRegrowthSystemTest {

  private val chunkSize = 32
  private val healRainMm = 40.0

  private lateinit var repository: ScorchRepository

  private fun registry(): ScorchRegistry {
    repository = mockk(relaxed = true) {
      every { findAll() } returns emptyList()
      every { save(any<ScorchMark>()) } answers { firstArg() }
    }

    val worldService = mockk<WorldService> {
      every { config } returns mockk { every { this@mockk.chunkSize } returns this@ScorchRegrowthSystemTest.chunkSize }
      every { record } returns mockk {
        every { shapeVersion } returns 1L
        every { pipelineVersion } returns 1L
      }
    }

    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    return ScorchRegistry(repository, executor, worldService)
  }

  /** An accumulator that adds a fixed amount of rain per sweep. */
  private fun rain(mmPerSweep: Double): RainAccumulator = mockk {
    every { healRainMm } returns this@ScorchRegrowthSystemTest.healRainMm
    every { advance(any(), any()) } answers {
      secondArg<Scar>().rainMm += mmPerSweep
    }
  }

  /** A square burn, wide enough that it takes several erosion steps to clear. */
  private fun burn(registry: ScorchRegistry, key: Long, half: Int = 4): Int {
    val mask = ColumnMask(chunkSize)
    for (y in 16 - half..16 + half) for (x in 16 - half..16 + half) mask.set(x, y)
    registry.burn(key, mask, burnedAtSecond = 0L)
    return mask.count
  }

  @Test
  fun `a dry region keeps its scar and costs no further writes`() {
    val registry = registry()
    val key = ScorchRegistry.columnKeyOf(1, 1)
    val burnt = burn(registry, key)

    val sut = ScorchRegrowthSystem(registry, rain(mmPerSweep = 0.0))
    repeat(10) { sut.update(testWorld(), 0f) }

    val scar = assertNotNull(registry.scarOf(key))
    assertEquals(burnt, scar.visible.count, "a scar shrank with no rain on it")
    verify(exactly = 1) { repository.save(any<ScorchMark>()) }
    verify(exactly = 0) { repository.deleteById(any()) }
  }

  @Test
  fun `rain shrinks a scar from its edges without writing`() {
    val registry = registry()
    val key = ScorchRegistry.columnKeyOf(1, 1)
    val burnt = burn(registry, key)

    // A sixth of the heal amount per sweep, so the first sweep is worth exactly one erosion step.
    val sut = ScorchRegrowthSystem(registry, rain(mmPerSweep = healRainMm / 6.0))
    sut.update(testWorld(), 0f)

    val scar = assertNotNull(registry.scarOf(key))
    assertTrue(scar.visible.count < burnt, "one step of rain removed nothing")
    assertTrue(scar.visible.count > 0, "one step of rain removed the whole scar")
    assertEquals(burnt, scar.mask.count, "the stored mask shrank; only `visible` may")
    verify(exactly = 1) { repository.save(any<ScorchMark>()) }
    verify(exactly = 0) { repository.deleteById(any()) }
  }

  @Test
  fun `enough rain heals a scar away and deletes its row`() {
    val registry = registry()
    val key = ScorchRegistry.columnKeyOf(1, 1)
    burn(registry, key)

    val sut = ScorchRegrowthSystem(registry, rain(mmPerSweep = healRainMm))
    repeat(10) { sut.update(testWorld(), 0f) }

    assertNull(registry.scarOf(key), "a fully rained-on scar is still in the registry")
    assertEquals(0, registry.scarredColumns)
    verify(exactly = 1) { repository.deleteById(key) }
  }

  /**
   * Progress past 1.0 keeps eroding rather than stalling, which is what lets a wide burn outlast a narrow one
   * instead of both clearing on the same sweep.
   */
  @Test
  fun `a wide scar takes more rain than a narrow one`() {
    val registry = registry()
    val narrow = ScorchRegistry.columnKeyOf(1, 1)
    val wide = ScorchRegistry.columnKeyOf(2, 2)
    burn(registry, narrow, half = 2)
    burn(registry, wide, half = 12)

    val sut = ScorchRegrowthSystem(registry, rain(mmPerSweep = healRainMm))
    sut.update(testWorld(), 0f)

    assertNull(registry.scarOf(narrow), "a small scar survived a full heal's worth of rain")
    assertNotNull(registry.scarOf(wide), "a 24-cell-wide scar cleared in one heal's worth of rain")
  }

  @Test
  fun `a sweep with nothing burnt does nothing at all`() {
    val registry = registry()
    val sut = ScorchRegrowthSystem(registry, rain(mmPerSweep = healRainMm))

    sut.update(testWorld(), 0f)

    verify(exactly = 0) { repository.save(any<ScorchMark>()) }
    verify(exactly = 0) { repository.deleteById(any()) }
  }
}
