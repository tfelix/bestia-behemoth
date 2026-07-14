package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Serializable, component-free snapshot of a persistable entity. Produced under the
 * world lock (so it must copy out plain values, never hold a live component reference)
 * and written to storage off the tick thread.
 */
interface EntitySnapshot {
  val entityId: EntityId
}

/**
 * Strategy for persisting and reloading one *kind* of entity (mob, dropped item,
 * master, ...). Concrete persisters are Spring beans collected as
 * `List<EntityPersister>` (the same bean-list mechanism the ECS uses for systems).
 *
 * ### Threading contract
 * - [supports] and [snapshot] are always invoked while the caller holds the world
 *   lock (inside a `world.read {}` block or on the tick thread). They must only read
 *   component state and must not perform any I/O.
 * - [persist] and [loadAll] run off the tick thread and may hit the database.
 *
 * Reconstruction reuses the existing entity factories and re-derives static stats from
 * templates, so a snapshot only needs to carry mutable state (position, current HP, ...).
 */
interface EntityPersister {
  /** Stable key identifying this persister; stored on the entity row to route loading. */
  val kind: String

  /** Whether entities of this kind are rehydrated into the world at server startup. */
  val loadsAtStartup: Boolean

  /** True if this persister is responsible for the given live entity. Called under the world lock. */
  fun supports(world: World, id: EntityId): Boolean

  /** Snapshots the entity into a serializable record. Called under the world lock; return null to skip. */
  fun snapshot(world: World, id: EntityId): EntitySnapshot?

  /** Persists a batch of snapshots produced by this persister. Runs off the tick thread. */
  fun persist(snapshots: List<EntitySnapshot>)

  /** Rehydrates all persisted entities of this kind into [world]. Runs at startup, before the socket opens. */
  fun loadAll(world: World)
}
