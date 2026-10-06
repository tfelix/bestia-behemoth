package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.entity.EntityVisual
import net.bestia.zone.ecs.item.GroundItemStack
import net.bestia.zone.ecs.persistence.persisters.MasterEntityPersister
import net.bestia.zone.ecs.persistence.persisters.PlayerBestiaEntityPersister
import net.bestia.zone.ecs.script.ScriptComponent
import net.bestia.zone.ecs.spawn.DenMember
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
  private val statusEffects: StatusEffectPersistenceService,
  private val asyncJobExecutor: AsyncJobExecutor,
) {

  private val persisters = foundPersisters.inClassNameOrder()

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
      if (onlyChanged && lastQueued[id] == queued) continue
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
      asyncJobExecutor.submit(key) { write(job) }
    }
  }

  /** Drops what is remembered about entities that are gone for good. */
  fun forget(ids: Collection<EntityId>) {
    ids.forEach(lastQueued::remove)
  }

  private fun write(job: WriteJob) {
    try {
      job.write(statusEffects)
    } catch (e: Exception) {
      forget(job.ids)
      throw e
    }
  }

  /** Status effects are compared too: a buff that ran out is a change even when nothing else moved. */
  private data class Queued(val snapshot: EntitySnapshot?, val effects: StatusEffectsSnapshot?)

  private class WriteJob {
    val ids = mutableListOf<EntityId>()
    private val snapshots = LinkedHashMap<EntityPersister, MutableList<EntitySnapshot>>()
    val effects = mutableListOf<StatusEffectsSnapshot>()

    fun add(persister: EntityPersister, snapshot: EntitySnapshot) {
      snapshots.getOrPut(persister) { mutableListOf() }.add(snapshot)
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

    /** What [persist] reads: every persister's `supports` check and snapshot, and the status effects. */
    val READS: ComponentClassSet = setOf(
      Account::class, EntityVisual::class, GroundItemStack::class, ScriptComponent::class, DenMember::class,
      StatusEffects::class,
    ) + MasterEntityPersister.SNAPSHOT_READS + PlayerBestiaEntityPersister.SNAPSHOT_READS
  }
}
