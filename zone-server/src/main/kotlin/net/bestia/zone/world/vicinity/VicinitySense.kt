package net.bestia.zone.world.vicinity

import net.bestia.zone.ecs.core.ComponentClassSet
import net.bestia.zone.ecs.core.World
import net.bestia.zone.util.EntityId

/**
 * Something an entity with a [Vicinity] notices about the ground around it, as a Spring bean.
 *
 * Told only about columns the entity has just come near, so a sense pays per chunk crossed rather than per
 * tick. A sense that also reacts to changes finds who is near in [afterUpdate] with [VicinityIndex.sensorsIn].
 * It runs inside [VicinitySystem] and may only read the components it names in [reads].
 */
interface VicinitySense {

  val reads: ComponentClassSet
    get() = emptySet()

  fun onEntered(world: World, entity: EntityId, columns: Collection<ChunkColumn>)

  /** Once a tick, after every vicinity is current. */
  fun afterUpdate(world: World) {
  }
}
