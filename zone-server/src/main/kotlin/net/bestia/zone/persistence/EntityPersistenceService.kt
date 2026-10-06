package net.bestia.zone.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.TickBuckets
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import net.bestia.zone.ecs.persistence.StatusEffectPersistenceService

/**
 * Saves every live [Persistent] entity once per interval, and deletes the rows of the ones that are gone.
 *
 * Runs where the world may be read - on the tick from [EntityPersistenceSystem], or at shutdown. Snapshots
 * are taken there; [EntityWriteBehind] writes them on the DB executor and skips what has not changed.
 *
 * Takes the [World] per call rather than injecting it, so the system can use this without a bean cycle.
 */
@Service
class EntityPersistenceService(
  private val writeBehind: EntityWriteBehind,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val deletionQueue: PersistedEntityDeletionQueue,
  private val persistedEntityRepository: PersistedEntityRepository,
  private val statusEffectPersistenceService: StatusEffectPersistenceService,
) {

  /** Saves the share of entities due on this [sweep], so one interval spreads the whole population over its sweeps. */
  fun syncDue(world: World, sweep: Long, sweepsPerInterval: Long) {
    pruneRemovedEntities()

    val due = ArrayList<EntityId>()
    world.query(Persistent::class).each { id ->
      if (TickBuckets.isDue(sweep, id, sweepsPerInterval)) due.add(id)
    }

    if (due.isNotEmpty()) {
      writeBehind.persist(world, due, onlyChanged = true)
    }
  }

  /** Saves every entity that changed since it was last saved. At shutdown, and for tests. */
  fun syncAll(world: World) {
    pruneRemovedEntities()

    val ids = ArrayList<EntityId>()
    world.query(Persistent::class).each { id -> ids.add(id) }

    writeBehind.persist(world, ids, onlyChanged = true)
    LOG.debug { "Entity persistence sync queued ${ids.size} entities" }
  }

  /** Deletes the rows of entities that have been permanently removed from the world. */
  private fun pruneRemovedEntities() {
    val removed = deletionQueue.drainAll()
    if (removed.isEmpty()) {
      return
    }
    writeBehind.forget(removed)

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
