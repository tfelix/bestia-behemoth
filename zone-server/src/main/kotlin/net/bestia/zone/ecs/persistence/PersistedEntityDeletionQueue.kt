package net.bestia.zone.ecs.persistence

import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Thread-safe hand-off of entity ids that were permanently removed from the world (mob death,
 * item pickup) and whose persisted blob rows should be pruned. Producers (systems on the tick
 * thread) [enqueue] without blocking; the [EntityPersistenceService] [drainAll]s and deletes the
 * rows off the tick thread on its next sync cycle, so no DB I/O ever happens during a tick.
 */
@Service
class PersistedEntityDeletionQueue {
  private val queue = ConcurrentLinkedQueue<EntityId>()

  fun enqueue(id: EntityId) {
    queue.add(id)
  }

  fun drainAll(): List<EntityId> {
    val out = ArrayList<EntityId>()
    while (true) {
      out.add(queue.poll() ?: break)
    }
    return out
  }
}
