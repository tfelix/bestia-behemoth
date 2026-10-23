package net.bestia.zone.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import net.bestia.zone.util.inClassNameOrder

/**
 * Takes entity snapshots where the world may be read, and writes them off the tick. All writes about
 * one owner share its [EntitySnapshot.writeKey], so they land in the order they were taken.
 */
@Service
class EntityWriteBehind(
  foundPersisters: List<EntityPersister>,
  private val statusEffects: EntitySidecar,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val metrics: PersistenceMetrics = PersistenceMetrics(),
) {

  private val persisters = foundPersisters.inClassNameOrder()

  /** What [persist] reads: every persister's `supports` check and snapshot, and the status effects. */
  val reads: ComponentClassSet = persisters.flatMap { it.reads }.toSet() + statusEffects.reads

  /**
   * What was last queued for each entity. A periodic save compares against it and skips what has not
   * changed; a failed write forgets its entities, so the next save writes them again.
   */
  private val lastQueued = ConcurrentHashMap<EntityId, Queued>()

  /**
   * Snapshots [ids] now and queues their writes. Call it on the tick or inside a world scope.
   *
   * @param onlyChanged skip an entity whose snapshot equals the one last queued for it.
   */
  fun persist(
    world: World,
    ids: Collection<EntityId>,
    withStatusEffects: Boolean = true,
    onlyChanged: Boolean = false,
  ) {
    val jobs = LinkedHashMap<Any, WriteJob>()

    for (id in ids) {
      val persister = persisters.firstOrNull { it.supports(world, id) }
      val snapshot = persister?.snapshot(world, id)
      if (persister == null) {
        LOG.debug { "Found no persistence handler for entity: $id, it will not be persisted" }
      }
      val effects = if (withStatusEffects) statusEffects.snapshot(world, id) else null

      val queued = Queued(snapshot, effects)
      if (onlyChanged && lastQueued[id] == queued) {
        metrics.unchanged()
        continue
      }
      if (snapshot == null && effects == null) continue
      lastQueued[id] = queued

      val job = jobs.getOrPut(snapshot?.writeKey ?: EntitySnapshot.SHARED_WRITE_KEY) { WriteJob() }
      job.ids.add(id)
      if (persister != null && snapshot != null) {
        job.add(persister, snapshot)
      }
      effects?.let(job.effects::add)
    }

    for ((key, job) in jobs) {
      val pending = metrics.queued(job.ids.size)

      // A write the executor dropped must not stay remembered as queued, or the entity is skipped as unchanged
      // until it next changes and a crash loses it.
      if (!asyncJobExecutor.trySubmit(key) { write(job, pending) }) {
        metrics.rejected(pending)
        forget(job.ids)
      }
    }
  }

  /** Drops what is remembered about entities that are gone for good. */
  fun forget(ids: Collection<EntityId>) {
    ids.forEach(lastQueued::remove)
  }

  private fun write(job: WriteJob, pending: PersistenceMetrics.Pending) {
    val started = System.nanoTime()
    var ok = false

    try {
      job.write(statusEffects)
      ok = true
    } catch (e: Exception) {
      forget(job.ids)
      throw e
    } finally {
      metrics.finished(pending, ok, System.nanoTime() - started)
    }
  }

  /** Status effects are compared too: a buff that ran out is a change even when nothing else moved. */
  private data class Queued(val snapshot: EntitySnapshot?, val effects: EntitySnapshot?)

  private class WriteJob {
    val ids = mutableListOf<EntityId>()
    private val snapshots = LinkedHashMap<EntityPersister, MutableList<EntitySnapshot>>()
    val effects = mutableListOf<EntitySnapshot>()

    fun add(persister: EntityPersister, snapshot: EntitySnapshot) {
      snapshots.getOrPut(persister) { mutableListOf() }.add(snapshot)
    }

    fun write(statusEffects: EntitySidecar) {
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
