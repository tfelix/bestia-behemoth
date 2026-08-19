package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.entity.PersistedEntityRepository
import net.bestia.zone.entity.deleteAllByEntityIdIn
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
      val effectSnapshots = mutableListOf<StatusEffectsSnapshot>()

      // Snapshot this batch under the lock; do not do I/O here.
      world.read {
        for (id in batch) {
          if (!isAlive(id)) {
            continue
          }

          // Cross-cutting, so it is taken for every persistent entity regardless of which kind
          // persister (if any) claims it — see StatusEffectPersistenceService.
          statusEffectPersistenceService.snapshot(this, id)?.let(effectSnapshots::add)

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
      statusEffectPersistenceService.persist(effectSnapshots)
      batches++
      Thread.yield() // give the tick thread room between batches
    }

    LOG.debug { "Entity persistence sync flushed $persisted entities across $batches batch(es)" }
  }

  /**
   * Deletes the rows of entities that have been permanently removed from the world.
   *
   * Caught rather than propagated, and that is the whole point of the `try`: this runs *first* in a cycle,
   * and [PersistedEntityDeletionQueue.drainAll] has already emptied the queue by the time anything can
   * fail. Letting a prune failure out would abort the snapshot phase of the same cycle - so one bad batch
   * of ids would stop the server persisting *anything*, permanently, and the queue those ids came from is
   * already gone so it would not even retry. Losing a few stale rows is the far cheaper failure, and the
   * error log is what makes it visible rather than silent.
   */
  private fun pruneRemovedEntities() {
    val removed = deletionQueue.drainAll()
    if (removed.isEmpty()) {
      return
    }

    try {
      persistedEntityRepository.deleteAllByEntityIdIn(removed)
      statusEffectPersistenceService.deleteFor(removed)
      LOG.debug { "Pruned ${removed.size} persisted row(s) for removed entities" }
    } catch (e: Exception) {
      LOG.error(e) { "Failed to prune ${removed.size} persisted row(s) for removed entities: ${e.message}" }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
