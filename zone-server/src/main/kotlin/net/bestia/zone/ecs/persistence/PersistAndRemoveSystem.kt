package net.bestia.zone.ecs.persistence

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World

import org.springframework.stereotype.Component as SpringComponent

/**
 * Persists-then-removes entities tagged [PersistAndRemove] (added on disconnect). The snapshot is taken
 * here, before the destroy; the write goes through [EntityWriteBehind], off the tick and in order with
 * every other write about the same owner.
 */
@SpringComponent
class PersistAndRemoveSystem(
  private val writeBehind: EntityWriteBehind,
) : System {
  override val phase = Phase.PERSIST

  override val reads: ComponentClassSet = setOf(PersistAndRemove::class) + EntityWriteBehind.READS

  override fun update(world: World, deltaTime: Float) {
    val toRemove = mutableListOf<EntityId>()
    world.query(PersistAndRemove::class).each { id -> toRemove.add(id) }
    if (toRemove.isEmpty()) return

    writeBehind.persist(world, toRemove)
    toRemove.forEach(world::destroy)
  }
}
