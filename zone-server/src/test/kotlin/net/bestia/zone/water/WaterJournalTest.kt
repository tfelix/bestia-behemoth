package net.bestia.zone.water

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.water.persistence.HeldWaterChunk
import net.bestia.zone.water.persistence.HeldWaterChunkRepository
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.PersistedWorld
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WaterJournalTest {

  private val water = mockk<WaterService>(relaxed = true)
  private val repository = mockk<HeldWaterChunkRepository>(relaxed = true)
  private val record = mockk<PersistedWorld> {
    every { shapeVersion } returns SHAPE
    every { pipelineVersion } returns PIPELINE
  }
  private val worldService = mockk<WorldService> { every { record } returns this@WaterJournalTest.record }
  private val jobs = AsyncJobExecutor(workerCount = 1)

  private val sut = WaterJournal(water, repository, worldService, jobs)

  @AfterEach
  fun shutDown() {
    jobs.shutdown()
  }

  private fun row(chunk: ChunkPos, shape: Long = SHAPE): HeldWaterChunk {
    return HeldWaterChunk(HeldWaterChunk.Key.of(chunk), shape, PIPELINE)
  }

  @Test
  fun `held chunks of this world are resumed and those of another are dropped`() {
    val current = row(ChunkPos(1, 1))
    val otherWorld = row(ChunkPos(2, 2), shape = SHAPE + 1)
    every { repository.findAll() } returns listOf(current, otherWorld)

    sut.restoreAll()

    verify { repository.deleteAll(listOf(otherWorld)) }
    verify { water.resume(setOf(ChunkPos(1, 1))) }
  }

  @Test
  fun `a flush writes only what changed since the last one`() {
    every { repository.findAll() } returns listOf(row(ChunkPos(1, 1)))
    sut.restoreAll()
    every { water.heldPositions() } returns setOf(ChunkPos(2, 2))

    assertEquals(2, sut.flush())
    jobs.awaitPending("water-held-chunks")

    verify { repository.saveAll(match<List<HeldWaterChunk>> { rows -> rows.single().chunk == ChunkPos(2, 2) }) }
    verify { repository.deleteAllById(listOf(HeldWaterChunk.Key.of(ChunkPos(1, 1)))) }
    assertEquals(0, sut.flush(), "nothing changed since")
  }

  private companion object {
    const val SHAPE = 11L
    const val PIPELINE = 22L
  }
}
