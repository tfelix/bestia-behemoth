package net.bestia.zone.persistence

import net.bestia.zone.util.EntityId
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Thread-safe hand-off of entity ids that were permanently removed from the world (mob death,
 * item pickup) and whose persisted blob rows should be pruned. Producers (systems on the tick
 * thread) [enqueue] without blocking; the [EntityPersistenceService] [drainAll]s and deletes the
 * rows off the tick thread on its next sync cycle, so no DB I/O ever happens during a tick.
 *
 * TODO check how and if this works because this seems not like a good design
 */
@Service
class PersistedEntityDeletionQueue {
  private val queue = ConcurrentLinkedQueue<EntityId>()
  private val waiting = AtomicInteger()

  /** Ids waiting for the next sync to delete their rows. */
  val pending: Int
    get() {
      return waiting.get()
    }

  fun enqueue(id: EntityId) {
    queue.add(id)
    waiting.incrementAndGet()
  }

  fun drainAll(): List<EntityId> {
    val out = ArrayList<EntityId>()
    while (true) {
      out.add(queue.poll() ?: break)
      waiting.decrementAndGet()
    }
    return out
  }
}
