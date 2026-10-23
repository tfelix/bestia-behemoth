package net.bestia.zone.persistence

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.Phase
import net.bestia.zone.ecs.core.System
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

  override val reads: ComponentClassSet = setOf(PersistAndRemove::class) + writeBehind.reads

  override fun update(world: World, deltaTime: Float) {
    val toRemove = world.query(PersistAndRemove::class).map { id -> id }
    if (toRemove.isEmpty()) return

    writeBehind.persist(world, toRemove)
    toRemove.forEach(world::destroy)
  }
}
