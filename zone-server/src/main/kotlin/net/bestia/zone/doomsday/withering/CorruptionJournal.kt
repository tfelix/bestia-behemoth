package net.bestia.zone.doomsday.withering

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.doomsday.persistence.StoredCorruptionTile
import net.bestia.zone.doomsday.persistence.StoredCorruptionTileRepository
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Service

/** Keeps the corruption across a restart: writes changed tiles out, and loads them at boot. */
@Service
class CorruptionJournal(
  private val field: CorruptionField,
  private val repository: StoredCorruptionTileRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Boot only. Deletes the tiles of another world. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }
    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
    }
    current.forEach { row -> runCatching { field.restore(row.tileKey, row.cells()) } }
    LOG.info { "Loaded ${field.tileCount} corruption tile(s)" }
  }

  /** Writes the tiles changed since the last call and deletes the healed ones. */
  fun flush() {
    val changes = field.takeChanges()
    if (changes.written.isEmpty() && changes.dropped.isEmpty()) {
      return
    }

    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val rows = changes.written.map { (key, cells) ->
      StoredCorruptionTile(key, StoredCorruptionTile.bytesOf(cells), shapeVersion, pipelineVersion)
    }
    asyncJobExecutor.submit(JOB_KEY) {
      repository.saveAll(rows)
      repository.deleteAllById(changes.dropped)
    }
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    const val JOB_KEY = "corruption-tiles"
  }
}
