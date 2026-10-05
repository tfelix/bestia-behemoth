package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.entity.deleteAllByEntityIdIn
import net.bestia.zone.util.EntityId
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Periodically snapshots every live [Persistent] entity to durable storage in bounded batches.
 *
 * Runs off the tick thread (driven by `@Scheduled`). For each batch it takes the world lock only long
 * enough to copy component state into [EntitySnapshot]s; [EntityWriteBehind] then writes them on the
 * DB executor. Batching keeps a single lock acquisition small even with thousands of entities.
 *
 * TODO we should benchmark this with ~1m entities
 */
@Service
class EntityPersistenceService(
  private val world: WorldView,
  private val writeBehind: EntityWriteBehind,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val config: EntityPersistenceConfig,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val persistedEntityRepository: PersistedEntityRepository,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
) {

  @Scheduled(
    initialDelayString = "\${persistence.initial-delay-ms}",
    fixedDelayString = "\${persistence.interval-ms}"
  )
  fun scheduledSync() {
    try {
      syncOnce()
    } catch (e: Exception) {
      LOG.error(e) { "Periodic entity persistence sync failed: ${e.message}" }
    }
  }

  /** Runs one full sync cycle: snapshots now, writes queued. Exposed for tests and boot-time flushing. */
  fun syncOnce() {
    pruneRemovedEntities()

    val ids = mutableListOf<EntityId>()
    world.read { query(Persistent::class).each { id -> ids.add(id) } }
    if (ids.isEmpty()) {
      return
    }

    for (batch in ids.chunked(config.batchSize)) {
      world.read { writeBehind.persist(this, batch.filter { isAlive(it) }) }
      Thread.yield() // give the tick thread room between batches
    }

    LOG.debug { "Entity persistence sync queued ${ids.size} entities" }
  }

  /** Deletes the rows of entities that have been permanently removed from the world. */
  private fun pruneRemovedEntities() {
    val removed = deletionQueue.drainAll()
    if (removed.isEmpty()) {
      return
    }

    // On the shared row key, so the delete lands after any write of the same rows queued before it.
    asyncJobExecutor.submit(EntitySnapshot.SHARED_WRITE_KEY) {
      try {
        persistedEntityRepository.deleteAllByEntityIdIn(removed)
        statusEffectPersistenceService.deleteFor(removed)
        LOG.debug { "Pruned ${removed.size} persisted row(s) for removed entities" }
      } catch (e: Exception) {
        LOG.error(e) { "Failed to prune ${removed.size} persisted row(s) for removed entities: ${e.message}" }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
