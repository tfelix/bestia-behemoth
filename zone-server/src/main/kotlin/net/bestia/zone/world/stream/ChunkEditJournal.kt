package net.bestia.zone.world.stream

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** Keeps terrain edits across a restart: writes edited chunks out, and reads them back at boot. */
@Service
class ChunkEditJournal(
  private val chunkService: ChunkService,
  private val repository: PersistedChunkEditRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /**
   * Restores the saved edits into [ChunkService]. At boot, before the tick runs: a chunk edited this run
   * cannot take a saved edit any more.
   */
  fun restoreAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} terrain edits made on another world or pipeline" }
    }

    var restored = 0
    for (row in current) {
      // Kept, not deleted: a build that can read it again should get it back.
      runCatching { chunkService.restore(row.toSavedEdit()) }
        .onSuccess { restored++ }
        .onFailure { e -> LOG.error(e) { "Terrain edit ${row.id} cannot be restored and is skipped" } }
    }

    LOG.info { "Restored $restored edited chunks" }
  }

  /** Writes every chunk edited since the last call. On the tick or inside a lease; the write is a DB job. */
  fun flushDirty(): Int {
    val edits = chunkService.drainUnsaved()
    if (edits.isEmpty()) return 0

    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion

    for (saved in edits) {
      asyncJobExecutor.submit(saved.chunk) {
        repository.save(PersistedChunkEdit.of(saved, shapeVersion, pipelineVersion))
      }
    }

    return edits.size
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
