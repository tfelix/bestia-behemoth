package net.bestia.zone.battle.ecs.status

import net.bestia.zone.util.EntityId
import java.util.concurrent.ConcurrentHashMap
import org.springframework.stereotype.Component as SpringComponent

/**
 * Work done since [WorkStrainSystem] last charged for it, in seconds of labour per worker.
 *
 * A ledger rather than a component, because many systems in many phases finish a unit of work, and a component
 * they all wrote would force an order on all of them for nothing. Systems may record from parallel waves.
 */
@SpringComponent
class LabourLedger {

  private val secondsByWorker = ConcurrentHashMap<EntityId, Float>()

  fun record(workerId: EntityId, seconds: Float) {
    if (seconds <= 0f) return

    secondsByWorker.merge(workerId, seconds, Float::plus)
  }

  /** Everything recorded so far, which is then forgotten. */
  fun drain(): Map<EntityId, Float> {
    val drained = HashMap<EntityId, Float>()
    secondsByWorker.keys.forEach { id -> secondsByWorker.remove(id)?.let { drained[id] = it } }
    return drained
  }
}
