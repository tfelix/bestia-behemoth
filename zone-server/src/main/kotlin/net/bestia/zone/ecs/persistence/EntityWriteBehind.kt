package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service

/**
 * Takes entity snapshots where the world may be read, and writes them off the tick. All writes about
 * one owner share its [EntitySnapshot.writeKey], so they land in the order they were taken.
 *
 * Takes the [World] per call rather than injecting it, so systems can use this without a bean cycle.
 */
@Service
class EntityWriteBehind(
  private val persisters: List<EntityPersister>,
  private val statusEffects: StatusEffectPersistenceService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  /** Snapshots [ids] now and queues their writes. Call it on the tick or inside a world scope. */
  fun persist(world: World, ids: Collection<EntityId>, withStatusEffects: Boolean = true) {
    val jobs = LinkedHashMap<Any, WriteJob>()

    for (id in ids) {
      val persister = persisters.firstOrNull { it.supports(world, id) }
      val snapshot = persister?.snapshot(world, id)
      if (persister == null) {
        LOG.debug { "Found no persistence handler for entity: $id, it will not be persisted" }
      }

      val job = jobs.getOrPut(snapshot?.writeKey ?: EntitySnapshot.SHARED_WRITE_KEY) { WriteJob() }
      if (persister != null && snapshot != null) {
        job.add(persister, snapshot)
      }
      if (withStatusEffects) {
        statusEffects.snapshot(world, id)?.let(job.effects::add)
      }
    }

    for ((key, job) in jobs) {
      if (job.isEmpty()) continue
      asyncJobExecutor.submit(key) { job.write(statusEffects) }
    }
  }

  private class WriteJob {
    private val snapshots = LinkedHashMap<EntityPersister, MutableList<EntitySnapshot>>()
    val effects = mutableListOf<StatusEffectsSnapshot>()

    fun add(persister: EntityPersister, snapshot: EntitySnapshot) {
      snapshots.getOrPut(persister) { mutableListOf() }.add(snapshot)
    }

    fun isEmpty(): Boolean {
      return snapshots.isEmpty() && effects.isEmpty()
    }

    fun write(statusEffects: StatusEffectPersistenceService) {
      snapshots.forEach { (persister, batch) -> persister.persist(batch) }
      if (effects.isNotEmpty()) {
        statusEffects.persist(effects)
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
