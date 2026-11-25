package net.bestia.zone.world.mana

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.persistence.AsyncJobExecutor
import net.bestia.zone.world.WorldService
import net.bestia.zone.world.persistence.StoredManaTile
import net.bestia.zone.world.persistence.StoredManaTileRepository
import org.springframework.stereotype.Service

/** Keeps the mana field across a restart: writes changed tiles out, and loads them at boot. */
@Service
class ManaJournal(
  private val field: ManaField,
  private val repository: StoredManaTileRepository,
  private val worldService: WorldService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Boot only, before the loop starts. Deletes the tiles of another world. */
  fun loadAll() {
    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val (current, stale) = repository.findAll().partition {
      it.worldShapeVersion == shapeVersion && it.pipelineVersion == pipelineVersion
    }

    if (stale.isNotEmpty()) {
      repository.deleteAll(stale)
      LOG.warn { "Dropped ${stale.size} mana tile(s) of another world or pipeline" }
    }

    var skipped = 0
    current.forEach { row ->
      runCatching { field.restore(row.tileKey, row.cells()) }.onFailure { skipped++ }
    }

    if (skipped > 0) {
      LOG.error { "$skipped mana tile(s) have the wrong size; ignored" }
    }

    LOG.info { "Loaded ${field.tileCount} mana tile(s)" }
  }

  /** Writes the tiles changed since the last call and deletes the dropped ones. On the tick. */
  fun flush(): Int {
    val changes = field.takeChanges()
    if (changes.written.isEmpty() && changes.dropped.isEmpty()) return 0

    val shapeVersion = worldService.record.shapeVersion
    val pipelineVersion = worldService.record.pipelineVersion
    val rows = changes.written.map { (key, cells) ->
      StoredManaTile(key, StoredManaTile.bytesOf(cells), shapeVersion, pipelineVersion)
    }

    asyncJobExecutor.submit(JOB_KEY) {
      repository.saveAll(rows)
      repository.deleteAllById(changes.dropped)
    }

    return rows.size + changes.dropped.size
  }

  private companion object {
    private val LOG = KotlinLogging.logger { }

    /** One key for every flush, so two flushes can never land out of order. */
    const val JOB_KEY = "mana-tiles"
  }
}
