package net.bestia.zone.world.ground

import io.mockk.every
import io.mockk.mockk
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.environment.time.BestiaClock
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The blood store: the shape of a pool, and the two things it does not share with wear.
 *
 * It is soaked in once rather than accumulated, and it does not care what the ground is made of - a battle on
 * a road should leave a road that has been fought on.
 */
class GroundBloodRegistryTest {

  private val chunkSize = 32
  private val config = GroundBloodConfig()

  private var clockSecond = 0L

  private fun registry(): GroundBloodRegistry {
    val repository = mockk<GroundLayerMarkRepository>(relaxed = true) {
      every { findByIdColumnKey(any()) } returns emptyList()
      every { save(any<GroundLayerMark>()) } answers { firstArg() }
    }

    val worldService = mockk<WorldService> {
      every { config } returns mockk {
        every { this@mockk.chunkSize } returns this@GroundBloodRegistryTest.chunkSize
      }
      every { record } returns mockk {
        every { shapeVersion } returns 1L
        every { pipelineVersion } returns 2L
      }
    }

    val executor = mockk<AsyncJobExecutor> {
      every { submit(any(), any()) } answers { secondArg<() -> Unit>().invoke() }
    }

    val clock = mockk<BestiaClock> {
      every { now() } answers { mockk { every { absoluteSecond } returns clockSecond } }
    }

    return GroundBloodRegistry(repository, executor, worldService, config, clock)
  }

  private fun held(): GroundBloodRegistry = registry().apply {
    // Blood only lands in ground somebody is holding, as every graded layer does.
    for (y in -1..1) for (x in -1..1) track(ColumnKey.of(x, y))
  }

  @Test
  fun `a death soaks the tile it happened on hardest`() {
    val sut = held()

    sut.spill(voxelX = 10, voxelY = 10, nowSecond = 0)

    val levels = assertNotNull(sut.columnAt(ColumnKey.of(0, 0))).levels

    assertEquals(config.spillWeight, levels[10, 10], "the tile it died on gets the whole weight")
    assertTrue(levels[12, 10] in 1 until levels[10, 10], "the rim is faint rather than absent")
  }

  @Test
  fun `the pool is round, not square`() {
    val sut = held()

    sut.spill(voxelX = 10, voxelY = 10, nowSecond = 0)

    val levels = assertNotNull(sut.columnAt(ColumnKey.of(0, 0))).levels

    assertEquals(0, levels[12, 12], "a corner of the bounding box was soaked")
  }

  /** A pool two metres across reaches over a seam every thirty-two metres. */
  @Test
  fun `a pool on a chunk boundary announces both columns`() {
    val sut = held()

    val touched = sut.spill(voxelX = 31, voxelY = 10, nowSecond = 0)

    assertEquals(setOf(ColumnKey.of(0, 0), ColumnKey.of(1, 0)), touched)
  }

  @Test
  fun `blood nobody can see is not recorded`() {
    val sut = registry()

    assertTrue(sut.spill(voxelX = 10, voxelY = 10, nowSecond = 0).isEmpty())
    assertEquals(0, sut.markedColumns)
  }

  @Test
  fun `a stain dries out on its own and costs nothing while it does`() {
    val sut = held()
    sut.spill(voxelX = 10, voxelY = 10, nowSecond = 0)

    val column = assertNotNull(sut.columnAt(ColumnKey.of(0, 0)))
    val fresh = column.levels[10, 10]

    column.ageTo(config.fadeSeconds / 2, config.fadeSeconds)

    assertTrue(column.levels[10, 10] < fresh, "a stain half a life old is as dark as a fresh one")
    assertTrue(column.levels[10, 10] > 0, "it dried out in half the time it was given")

    column.ageTo(config.fadeSeconds, config.fadeSeconds)

    assertTrue(column.isEmpty, "the stain outlived its fade")
  }

  /** The layer id is what pairs this store with its row and its channel; nothing checks it at runtime. */
  @Test
  fun `it stores itself as the bloodied layer`() {
    assertEquals(GroundLayer.BLOODIED, registry().layer)
  }
}
