package net.bestia.zone.persistence

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * State saved for every persisted entity, whatever its kind: the status effects. It is written in the same
 * job as the entity, after the entity's [EntityPersister], and it is dropped with the entity.
 */
interface EntitySidecar {

  /** The component types [snapshot] reads. */
  val reads: ComponentClassSet

  /** Copies the state out, or null when the entity carries none. Called with the world to itself. */
  fun snapshot(world: World, entityId: EntityId): EntitySnapshot?

  /** Writes a batch of [snapshot] results. Runs off the tick thread. */
  fun persist(snapshots: List<EntitySnapshot>)

  /** Drops what is stored for entities that are gone for good. */
  fun deleteFor(entityIds: Collection<EntityId>)
}
