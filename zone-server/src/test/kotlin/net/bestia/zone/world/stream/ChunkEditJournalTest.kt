package net.bestia.zone.world.stream

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import net.bestia.worldgen.core.ChunkPos
import net.bestia.worldgen.derived.VoxelEdit
import net.bestia.worldgen.store.ChunkEdit
import net.bestia.worldgen.voxel.BlockType
import net.bestia.worldgen.voxel.Occupancy
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.persistence.PersistedWorld
import net.bestia.zone.world.WorldService
import org.junit.jupiter.api.AfterEach
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import net.bestia.zone.world.persistence.PersistedChunkEdit
import net.bestia.zone.world.persistence.PersistedChunkEditRepository

class ChunkEditJournalTest {

  private val chunkService = mockk<ChunkService>(relaxed = true)
  private val repository = mockk<PersistedChunkEditRepository>(relaxed = true)
  private val record = mockk<PersistedWorld> {
    every { shapeVersion } returns SHAPE
    every { pipelineVersion } returns PIPELINE
  }
  private val worldService = mockk<WorldService> { every { record } returns this@ChunkEditJournalTest.record }
  private val jobs = AsyncJobExecutor(workerCount = 1)

  private val sut = ChunkEditJournal(chunkService, repository, worldService, jobs)

  @AfterEach
  fun shutDown() {
    jobs.shutdown()
  }

  private fun row(chunk: ChunkPos, shape: Long = SHAPE, pipeline: Long = PIPELINE): PersistedChunkEdit {
    val saved = ChunkService.SavedEdit(chunk, 2, ChunkEdit.Delta(longArrayOf(water(1))))
    return PersistedChunkEdit.of(saved, shape, pipeline)
  }

  private fun water(voxelIndex: Int): Long {
    return VoxelEdit.pack(voxelIndex, BlockType.WATER, Occupancy.FULL)
  }

  @Test
  fun `edits of another world or pipeline are dropped, the rest restored`() {
    val current = row(ChunkPos(1, 1))
    val otherWorld = row(ChunkPos(2, 2), shape = SHAPE + 1)
    val otherPipeline = row(ChunkPos(3, 3), pipeline = PIPELINE + 1)
    every { repository.findAll() } returns listOf(current, otherWorld, otherPipeline)

    sut.restoreAll()

    verify { repository.deleteAll(listOf(otherWorld, otherPipeline)) }
    verify(exactly = 1) { chunkService.restore(match { it.chunk == ChunkPos(1, 1) }) }
  }

  @Test
  fun `a delta in the old removal-only format is dropped, not misread`() {
    val legacy = row(ChunkPos(1, 1)).apply { deltaFormat = 0 }
    val fine = row(ChunkPos(2, 2))
    every { repository.findAll() } returns listOf(legacy, fine)

    sut.restoreAll()

    verify { repository.deleteAll(listOf(legacy)) }
    verify(exactly = 1) { chunkService.restore(match { it.chunk == ChunkPos(2, 2) }) }
  }

  @Test
  fun `a saved delta reads back as the edits it was written from`() {
    val restored = row(ChunkPos(1, 1)).toSavedEdit().edit

    assertIs<ChunkEdit.Delta>(restored)
    assertContentEquals(longArrayOf(water(1)), restored.edits)
  }

  @Test
  fun `an edit that cannot be restored is skipped and kept`() {
    val unreadable = row(ChunkPos(1, 1)).apply { payload = byteArrayOf(0x7F) }
    val fine = row(ChunkPos(2, 2))
    every { repository.findAll() } returns listOf(unreadable, fine)

    sut.restoreAll()

    verify(exactly = 1) { chunkService.restore(match { it.chunk == ChunkPos(2, 2) }) }
    verify(exactly = 0) { repository.deleteAll(any<Iterable<PersistedChunkEdit>>()) }
  }

  @Test
  fun `an edited chunk is written with the world's versions`() {
    val saved = ChunkService.SavedEdit(ChunkPos(4, 5, 1), 3, ChunkEdit.Delta(longArrayOf(water(2), water(3))))
    every { chunkService.drainUnsaved() } returns listOf(saved)
    val written = slot<PersistedChunkEdit>()
    every { repository.save(capture(written)) } answers { written.captured }

    assertEquals(1, sut.flushDirty())
    jobs.awaitPending(saved.chunk)

    assertEquals(PersistedChunkEdit.Key(4, 5, 1), written.captured.id)
    assertEquals(3, written.captured.revision)
    assertEquals(SHAPE, written.captured.worldShapeVersion)
    assertEquals(PIPELINE, written.captured.pipelineVersion)
    assertEquals(ChunkEditCodec.FORMAT, written.captured.deltaFormat)
  }

  private companion object {
    const val SHAPE = 11L
    const val PIPELINE = 22L
  }
}
