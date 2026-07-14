package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.util.EntityId
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Periodically snapshots every live [Persistent] entity to durable storage in bounded batches.
 *
 * Runs off the tick thread (driven by `@Scheduled`). For each batch it takes the world lock only
 * long enough to copy component state into serializable [EntitySnapshot]s, then releases the lock
 * and performs the DB writes — the same snapshot-under-lock / IO-off-lock split `ZoneEngine` uses
 * for outbound sync. Batching keeps a single lock acquisition (and transaction) small even with
 * thousands of entities.
 *
 * TODO we should benchmark this with ~1m entities
 */
@Service
class EntityPersistenceService(
  private val world: WorldView,
  private val persisters: List<EntityPersister>,
  private val config: EntityPersistenceConfig,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val persistedEntityRepository: PersistedEntityRepository,
) {

  @Scheduled(
    initialDelayString = "\${persistence.initial-delay-ms:90000}",
    fixedDelayString = "\${persistence.interval-ms:90000}"
  )
  fun scheduledSync() {
    try {
      syncOnce()
    } catch (e: Exception) {
      LOG.error(e) { "Periodic entity persistence sync failed: ${e.message}" }
    }
  }

  /** Runs one full sync cycle synchronously. Exposed for tests and boot-time flushing. */
  fun syncOnce() {
    pruneRemovedEntities()

    val ids = mutableListOf<EntityId>()
    world.read { query(Persistent::class).each { id -> ids.add(id) } }
    if (ids.isEmpty()) {
      return
    }

    var persisted = 0
    var batches = 0
    // you can not go through all IDs here and
    for (batch in ids.chunked(config.batchSize)) {
      val byPersister = LinkedHashMap<EntityPersister, MutableList<EntitySnapshot>>()

      // Snapshot this batch under the lock; do not do I/O here.
      world.read {
        for (id in batch) {
          if (!isAlive(id)) {
            continue
          }
          val persister = persisters.firstOrNull { it.supports(this, id) }
            ?: continue
          val snapshot = persister.snapshot(this, id)
            ?: continue

          byPersister.getOrPut(persister) { mutableListOf() }.add(snapshot)
        }
      }

      // Write outside the lock.
      byPersister.forEach { (persister, snapshots) ->
        persister.persist(snapshots)
        persisted += snapshots.size
      }
      batches++
      Thread.yield() // give the tick thread room between batches
    }

    LOG.debug { "Entity persistence sync flushed $persisted entity/entities across $batches batch(es)" }
  }

  private fun pruneRemovedEntities() {
    val removed = deletionQueue.drainAll()
    if (removed.isNotEmpty()) {
      persistedEntityRepository.deleteByEntityIdIn(removed)
      LOG.debug { "Pruned ${removed.size} persisted row(s) for removed entities" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
