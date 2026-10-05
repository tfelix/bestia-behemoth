package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId
import kotlin.reflect.KClass

/**
 * What changed since the last sync: components that went dirty and positions that moved, in the order it
 * happened. An entry may be stale by the time it is drained - the component removed, or already sent - so
 * a reader checks the component again.
 *
 * Synchronized because a parallel wave reports from several threads at once; uncontended otherwise.
 */
class DirtyLog {

  private val sinks = HashMap<KClass<out Component>, DirtyFlag.Sink>()

  private var dirtiedIds = LongArray(INITIAL_CAPACITY)
  private var dirtiedTypes = arrayOfNulls<KClass<out Component>>(INITIAL_CAPACITY)
  private var dirtiedCount = 0

  private var movedIds = LongArray(INITIAL_CAPACITY)
  private var movedCount = 0

  val movedSink = DirtyFlag.Sink { entity -> addMoved(entity) }

  @Synchronized
  fun sinkFor(type: KClass<out Component>): DirtyFlag.Sink {
    return sinks.getOrPut(type) { DirtyFlag.Sink { entity -> addDirtied(entity, type) } }
  }

  @Synchronized
  private fun addDirtied(entity: EntityId, type: KClass<out Component>) {
    if (dirtiedCount == dirtiedIds.size) {
      dirtiedIds = dirtiedIds.copyOf(dirtiedCount * 2)
      dirtiedTypes = dirtiedTypes.copyOf(dirtiedCount * 2)
    }
    dirtiedIds[dirtiedCount] = entity
    dirtiedTypes[dirtiedCount] = type
    dirtiedCount++
  }

  @Synchronized
  private fun addMoved(entity: EntityId) {
    if (movedCount == movedIds.size) {
      movedIds = movedIds.copyOf(movedCount * 2)
    }
    movedIds[movedCount] = entity
    movedCount++
  }

  /** Hands every moved entity to [action] and forgets them; what moves meanwhile waits for the next call. */
  fun drainMoved(action: (EntityId) -> Unit) {
    val ids: LongArray
    val count: Int
    synchronized(this) {
      ids = movedIds.copyOf(movedCount)
      count = movedCount
      movedCount = 0
    }

    for (i in 0 until count) action(ids[i])
  }

  /** Hands every dirtied (entity, type) to [action] and forgets them; what dirties meanwhile waits too. */
  fun drainDirtied(action: (EntityId, KClass<out Component>) -> Unit) {
    val ids: LongArray
    val types: Array<KClass<out Component>?>
    val count: Int
    synchronized(this) {
      ids = dirtiedIds.copyOf(dirtiedCount)
      types = dirtiedTypes.copyOf(dirtiedCount)
      count = dirtiedCount
      dirtiedTypes.fill(null, 0, dirtiedCount)
      dirtiedCount = 0
    }

    for (i in 0 until count) action(ids[i], types[i]!!)
  }

  private companion object {
    const val INITIAL_CAPACITY = 256
  }
}
