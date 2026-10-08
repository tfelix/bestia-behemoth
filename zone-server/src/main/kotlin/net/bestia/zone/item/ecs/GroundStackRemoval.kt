package net.bestia.zone.item.ecs

import net.bestia.zone.ecs.core.World
import net.bestia.zone.persistence.PersistedEntityDeletionQueue
import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Component

/** Takes a stack off the ground for good. Its row goes too, or the next boot would load the stack again. */
@Component
class GroundStackRemoval(
  private val deletionQueue: PersistedEntityDeletionQueue,
) {

  fun remove(world: World, stackId: EntityId) {
    deletionQueue.enqueue(stackId)
    world.destroy(stackId)
  }
}
