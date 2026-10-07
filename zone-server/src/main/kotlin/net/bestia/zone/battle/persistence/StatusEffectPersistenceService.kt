package net.bestia.zone.battle.persistence

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.battle.status.StatusEffectDefinitionRegistry
import net.bestia.zone.battle.status.StatusEffectId
import net.bestia.zone.battle.status.StatusEffectScriptRegistry
import net.bestia.zone.battle.ecs.effects.ActiveStatusEffect
import net.bestia.zone.battle.ecs.effects.StatusEffects
import net.bestia.zone.battle.ecs.status.IsStatusValueDirty
import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.persistence.EntitySidecar
import net.bestia.zone.persistence.EntitySnapshot
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/** Component-free copy of one entity's [StatusEffects], safe to carry off the tick thread. */
data class StatusEffectsSnapshot(
  override val entityId: EntityId,
  val effects: List<Entry>
) : EntitySnapshot {
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
 * ### Why this is not an [net.bestia.zone.persistence.EntityPersister]
 * Both [net.bestia.zone.persistence.EntityPersistenceService] and [net.bestia.zone.persistence.PersistAndRemoveSystem] resolve exactly one persister per
 * entity (`persisters.firstOrNull { it.supports(world, id) }`), so a status-effect persister would
 * *compete* with the kind persister that owns the entity instead of composing with it. Status
 * effects are a cross-cutting concern of the component, not of the entity kind, so this service
 * runs alongside the kind persister at every persist site rather than becoming one of them.
 *
 * ### Threading
 * [snapshot] and [attach] touch components and must be called with the world to itself (inside a
 * `read`/`modify`/`createEntity` block, or on the tick thread). [seed], [load], [persist] and
 * [deleteFor] hit the database and must not be called inside a world scope.
 */
@Service
class StatusEffectPersistenceService(
  private val persistedStatusEffectRepository: PersistedStatusEffectRepository,
  private val statusEffectDefinitionRegistry: StatusEffectDefinitionRegistry,
  private val statusEffectScriptRegistry: StatusEffectScriptRegistry,
) : EntitySidecar {

  override val reads: ComponentClassSet = setOf(StatusEffects::class)

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

  /** Loads the stored effects of a single entity. Hits the DB — call before taking a world scope. */
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
   * [net.bestia.zone.battle.ecs.effects.StatusValueRecalcSystem] folds them into `StatusValues`/`Speed`
   * on the next tick. Called with the world to itself; does no I/O.
   *
   * Merges rather than replaces, so an effect the spawner already applied survives the restore.
   */
  fun attach(world: World, entityId: EntityId, effects: List<ActiveStatusEffect>) {
    if (effects.isEmpty()) {
      return
    }

    val live = world.get(entityId, StatusEffects::class)
    if (live == null) {
      world.add(entityId, StatusEffects(effects.toMutableList()))
    } else {
      live.restore(effects)
    }
    world.add(entityId, IsStatusValueDirty)
  }

  /**
   * Copies a live entity's effects out into plain values. Called with the world to itself; does no I/O.
   *
   * Returns null when the entity carries no [StatusEffects] component at all, which means "this
   * entity is not participating" rather than "this entity has no effects" — the distinction matters
   * because an empty snapshot deletes the stored rows.
   */
  override fun snapshot(world: World, entityId: EntityId): StatusEffectsSnapshot? {
    val statusEffects = world.get(entityId, StatusEffects::class) ?: return null

    return StatusEffectsSnapshot(
      entityId = entityId,
      effects = statusEffects.activeEffects.filter(::isPersisted).map {
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
  override fun persist(snapshots: List<EntitySnapshot>) {
    if (snapshots.isEmpty()) {
      return
    }

    persistedStatusEffectRepository.deleteByOwnerEntityIdIn(snapshots.map { it.entityId })

    val rows = snapshots.map { it as StatusEffectsSnapshot }.flatMap { snapshot ->
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
  override fun deleteFor(entityIds: Collection<EntityId>) {
    if (entityIds.isEmpty()) {
      return
    }

    persistedStatusEffectRepository.deleteByOwnerEntityIdIn(entityIds)
  }

  /**
   * What an [ActiveStatusEffect] copies from its catalog entry and its script is deliberately not stored: it is
   * re-derived here, so an edit to either can never be contradicted by a stale row.
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
      isSyncedToClient = definition.isSyncedToClient,
      shield = statusEffectScriptRegistry.getOrThrow(definition.script).shield,
      polarity = definition.polarity
    )
  }

  private fun isPersisted(effect: ActiveStatusEffect): Boolean {
    val definition = statusEffectDefinitionRegistry.findById(effect.definitionId) ?: return true

    return statusEffectScriptRegistry.getOrThrow(definition.script).isPersisted
  }

  private fun Float.toNullableSeconds(): Float? = if (isInfinite()) null else this

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
