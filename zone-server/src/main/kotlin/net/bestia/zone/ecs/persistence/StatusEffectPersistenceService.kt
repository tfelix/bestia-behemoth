package net.bestia.zone.ecs.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.ecs.battle.effects.ActiveStatusEffect
import net.bestia.zone.ecs.battle.effects.StatusEffects
import net.bestia.zone.ecs.battle.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.PersistedStatusEffect
import net.bestia.zone.entity.PersistedStatusEffectRepository
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Component-free copy of one entity's [StatusEffects], safe to carry off the tick thread. */
data class StatusEffectsSnapshot(
  val entityId: EntityId,
  val effects: List<Entry>
) {
  data class Entry(
    val definitionId: Long,
    val level: Int,
    /** Null means "never expires" — the relational spelling of [Float.POSITIVE_INFINITY]. */
    val remainingSeconds: Float?,
    val sourceEntityId: EntityId?
  )
}

/**
 * Durable storage for [StatusEffects], keyed by [EntityId] and blind to what kind of entity that id
 * belongs to — a mob, a dropped item and a player master all round-trip through the same table.
 *
 * ### Why this is not an [EntityPersister]
 * Both [EntityPersistenceService] and [PersistAndRemoveSystem] resolve exactly one persister per
 * entity (`persisters.firstOrNull { it.supports(world, id) }`), so a status-effect persister would
 * *compete* with the kind persister that owns the entity instead of composing with it. Status
 * effects are a cross-cutting concern of the component, not of the entity kind, so this service
 * runs alongside the kind persister at every persist site rather than becoming one of them.
 *
 * ### Threading
 * [snapshot] and [attach] touch components and must be called with the world lock held (inside a
 * `read`/`modify`/`createEntity` block, or on the tick thread). [seed], [load], [persist] and
 * [deleteFor] hit the database and must not be called under the lock.
 */
@Service
class StatusEffectPersistenceService(
  private val persistedStatusEffectRepository: PersistedStatusEffectRepository,
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
) {

  /**
   * Writes an effect for an entity that need not exist yet — the pre-spawn path. Duration comes
   * from the effect's script, the same source [net.bestia.zone.battle.StatusEffectService] uses at
   * runtime, so a seeded effect and an applied one cannot drift apart.
   *
   * Idempotent per definition: seeding an effect the entity already has stored is a no-op, matching
   * [net.bestia.zone.battle.status.StackBehavior.IGNORE_IF_PRESENT] which is what markers use.
   */
  @Transactional
  fun seed(entityId: EntityId, effect: StatusEffectId, level: Int = 1) {
    val definition = statusEffectDefinitionRegistry.getOrThrow(effect.id)
    val script = statusEffectScriptRegistry.getOrThrow(definition.script)

    val alreadyStored = persistedStatusEffectRepository.findAllByOwnerEntityId(entityId)
      .any { it.definitionId == effect.id }
    if (alreadyStored) {
      return
    }

    persistedStatusEffectRepository.save(
      PersistedStatusEffect(
        ownerEntityId = entityId,
        definitionId = effect.id,
        level = level,
        remainingSeconds = script.durationSeconds(level).toFloat().toNullableSeconds(),
        sourceEntityId = null
      )
    )
    LOG.debug { "Seeded status effect ${effect.name} for entity $entityId" }
  }

  /** Loads the stored effects of a single entity. Hits the DB — call before taking the world lock. */
  @Transactional(readOnly = true)
  fun load(entityId: EntityId): List<ActiveStatusEffect> =
    persistedStatusEffectRepository.findAllByOwnerEntityId(entityId).mapNotNull(::toActiveEffect)

  /** Loads the stored effects of every entity that has any, grouped by owner. */
  @Transactional(readOnly = true)
  fun loadAll(): Map<EntityId, List<ActiveStatusEffect>> =
    persistedStatusEffectRepository.findAll()
      .groupBy { it.ownerEntityId }
      .mapValues { (_, rows) -> rows.mapNotNull(::toActiveEffect) }
      .filterValues { it.isNotEmpty() }

  /**
   * Attaches previously [load]ed effects to a live entity and marks it for a status value recalc so
   * [net.bestia.zone.ecs.battle.effects.StatusValueRecalcSystem] folds them into `StatusValues`/`Speed`
   * on the next tick. Called under the world lock; does no I/O.
   */
  fun attach(world: World, entityId: EntityId, effects: List<ActiveStatusEffect>) {
    if (effects.isEmpty()) {
      return
    }

    world.add(entityId, StatusEffects(effects.toMutableList()))
    world.add(entityId, IsStatusValueDirty)
  }

  /**
   * Copies a live entity's effects out into plain values. Called under the world lock; does no I/O.
   *
   * Returns null when the entity carries no [StatusEffects] component at all, which means "this
   * entity is not participating" rather than "this entity has no effects" — the distinction matters
   * because an empty snapshot deletes the stored rows.
   */
  fun snapshot(world: World, entityId: EntityId): StatusEffectsSnapshot? {
    val statusEffects = world.get(entityId, StatusEffects::class) ?: return null

    return StatusEffectsSnapshot(
      entityId = entityId,
      effects = statusEffects.activeEffects.map {
        StatusEffectsSnapshot.Entry(
          definitionId = it.definitionId,
          level = it.level,
          remainingSeconds = it.remainingSeconds.toNullableSeconds(),
          sourceEntityId = it.sourceEntityId
        )
      }
    )
  }

  /**
   * Writes a batch of snapshots. Delete-then-insert per owner rather than a diff, so an effect that
   * expired or removed itself actually disappears from storage — that is what makes a one-shot
   * marker like [StatusEffectId.MASTER_INTRO_MARKER] stay gone.
   */
  @Transactional
  fun persist(snapshots: List<StatusEffectsSnapshot>) {
    if (snapshots.isEmpty()) {
      return
    }

    persistedStatusEffectRepository.deleteByOwnerEntityIdIn(snapshots.map { it.entityId })

    val rows = snapshots.flatMap { snapshot ->
      snapshot.effects.map {
        PersistedStatusEffect(
          ownerEntityId = snapshot.entityId,
          definitionId = it.definitionId,
          level = it.level,
          remainingSeconds = it.remainingSeconds,
          sourceEntityId = it.sourceEntityId
        )
      }
    }

    if (rows.isNotEmpty()) {
      persistedStatusEffectRepository.saveAll(rows)
    }
  }

  /** Drops every stored effect of the given entities, e.g. once they are gone for good. */
  @Transactional
  fun deleteFor(entityIds: Collection<EntityId>) {
    if (entityIds.isEmpty()) {
      return
    }

    persistedStatusEffectRepository.deleteByOwnerEntityIdIn(entityIds)
  }

  /**
   * [ActiveStatusEffect.isSyncedToClient] is deliberately not stored: it is a denormalized copy of
   * the catalog entry, so it is re-derived here and an edit to `status_effects.yml` can never be
   * contradicted by a stale row.
   */
  private fun toActiveEffect(row: PersistedStatusEffect): ActiveStatusEffect? {
    val definition = statusEffectDefinitionRegistry.findById(row.definitionId)
    if (definition == null) {
      // An effect that was retired from the catalog since the row was written. Dropping it here
      // keeps the stale id out of the world; the next persist of this entity clears the row.
      LOG.warn { "Dropping persisted status effect ${row.definitionId} of ${row.ownerEntityId}: no such definition" }
      return null
    }

    return ActiveStatusEffect(
      definitionId = row.definitionId,
      level = row.level,
      remainingSeconds = row.remainingSeconds ?: Float.POSITIVE_INFINITY,
      sourceEntityId = row.sourceEntityId,
      isSyncedToClient = definition.isSyncedToClient
    )
  }

  private fun Float.toNullableSeconds(): Float? = if (isInfinite()) null else this

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
