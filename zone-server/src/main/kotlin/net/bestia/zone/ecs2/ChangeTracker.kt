package net.bestia.zone.ecs2

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Records which entities had a component added / changed / removed during a
 * tick, and lets external code react to that. This is the outbound half of the
 * "easy external messaging" goal — it centralises what the existing `Dirtyable`
 * + `DirtyComponentUpdateSystem` pair does, so the network layer no longer needs
 * every component to implement its own dirty bookkeeping.
 *
 * Two consumption styles share the same buffers:
 *  - **pull**: [drain] a type after the tick (e.g. a test or a bespoke sender).
 *  - **push**: register an observer via [on] and call [publish] once per tick to
 *    fan changes out to it.
 *
 * Marking is thread-safe (systems in a parallel wave may each mark their own
 * component types). Draining/publishing happen single-threaded between ticks.
 */
class ChangeTracker {
  private val marks = ConcurrentHashMap<KClass<out Component>, MutableSet<EntityId>>()
  private val observers = HashMap<KClass<out Component>, MutableList<(EntityId) -> Unit>>()

  fun mark(type: KClass<out Component>, id: EntityId) {
    marks.computeIfAbsent(type) { ConcurrentHashMap.newKeySet() }.add(id)
  }

  fun on(type: KClass<out Component>, handler: (EntityId) -> Unit) {
    observers.getOrPut(type) { ArrayList() }.add(handler)
  }

  /** Consumes and clears the changed ids for [type] (pull model). */
  fun drain(type: KClass<out Component>, action: (EntityId) -> Unit) {
    val set = marks[type] ?: return
    val snapshot = set.toLongArray()
    set.clear()
    for (id in snapshot) action(id)
  }

  /** Fires registered observers for all marked ids, then clears them (push model). */
  fun publish() {
    for ((type, set) in marks) {
      val obs = observers[type] ?: continue
      val snapshot = set.toLongArray()
      set.clear()
      for (id in snapshot) {
        for (handler in obs) handler(id)
      }
    }
  }

  fun forget(id: EntityId) {
    for (set in marks.values) set.remove(id)
  }

  fun clear() {
    marks.values.forEach { it.clear() }
  }

  private fun Set<EntityId>.toLongArray(): LongArray {
    val out = LongArray(size)
    var i = 0
    for (id in this) out[i++] = id
    return out
  }
}
