package net.bestia.zone.ecs

import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.util.EntityId

/**
 * An [EntityVisibility] that only remembers what it was told, for the tests that care whether [ZoneEngine]
 * announced an entity's move at all - the real answer needs a generated world and a chunk subscription.
 */
class RecordingEntityVisibility : EntityVisibility {

  val moves = mutableListOf<Pair<EntityId, Vec3L>>()
  val forgotten = mutableListOf<EntityId>()
  val reannounced = mutableListOf<Long>()

  override fun reannounce(accountId: Long) {
    reannounced.add(accountId)
  }

  override fun moved(entityId: EntityId, position: Vec3L) {
    moves.add(entityId to position)
  }

  override fun forgot(entityId: EntityId) {
    forgotten.add(entityId)
  }

  /** Who the engine should treat as watching; the tests that care set this per entity. */
  var observers: MutableMap<EntityId, Set<Long>> = mutableMapOf()

  override fun observersOf(entityId: EntityId): Set<Long> = observers[entityId] ?: emptySet()

  /** Handed out once, so a test can assert the engine sent a snapshot for it. */
  var queued: List<EntityVisibility.Delivery> = emptyList()

  override fun drain(): List<EntityVisibility.Delivery> {
    val drained = queued
    queued = emptyList()

    return drained
  }
}
