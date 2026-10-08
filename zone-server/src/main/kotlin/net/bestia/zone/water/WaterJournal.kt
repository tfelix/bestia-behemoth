package net.bestia.zone.water

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.worldgen.core.ChunkPos
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.water.persistence.HeldWaterChunk
import net.bestia.zone.water.persistence.HeldWaterChunkRepository
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** Keeps the chunks the water simulation holds across a restart: writes them out, and resumes them at boot. */
@Service
class WaterJournal(
  private val water: WaterService,
  private val repository: HeldWaterChunkRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** What the table holds as far as this process knows, so a flush writes only the difference. */
  private var written: Set<ChunkPos> = emptySet()

  /** Hands the saved chunks back to the simulation. At boot, after the chunk edits are restored. */
  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} held water chunks of another world or pipeline" }
    }

    written = current.map { it.chunk }.toSet()
    water.resume(written)
    LOG.info { "Resumed water in ${written.size} chunks" }
  }

  /** Writes the held chunks where they changed since the last call. On the tick or inside a lease. */
  fun flush(): Int {
    val held = water.heldPositions()
    val added = held - written
    val released = written - held
    if (added.isEmpty() && released.isEmpty()) return 0

    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val rows = added.map { HeldWaterChunk(HeldWaterChunk.Key.of(it), shapeVersion, pipelineVersion) }
    val gone = released.map(HeldWaterChunk.Key::of)

    asyncJobExecutor.submit(JOB_KEY) {
      repository.saveAll(rows)
      repository.deleteAllById(gone)
    }

    written = held
    return added.size + released.size
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every flush, so two flushes can never land out of order. */
    const val JOB_KEY = "water-held-chunks"
  }
}
