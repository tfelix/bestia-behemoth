package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

/**
 * Records which entities had a component added / changed / removed during a
 * tick, and lets external code react to that. This is the outbound half of the
 * "easy external messaging" goal — it centralises what the existing `Dirtyable`
 * + `DirtyComponentUpdateSystem` pair does, so the network layer no longer needs
 * every component to implement its own dirty bookkeeping.
 *
 * Pull model only: [drain] a type after the tick (e.g. a test or a bespoke
 * sender) to consume and clear the entities marked changed for it.
 *
 * Marking is thread-safe (systems in a parallel wave may each mark their own
 * component types). Draining happens single-threaded between ticks.
 */
class ChangeTracker {
  private val marks = ConcurrentHashMap<KClass<out Component>, MutableSet<EntityId>>()

  fun mark(type: KClass<out Component>, id: EntityId) {
    marks.computeIfAbsent(type) { ConcurrentHashMap.newKeySet() }.add(id)
  }

  /** Consumes and clears the changed ids for [type] (pull model). */
  fun drain(type: KClass<out Component>, action: (EntityId) -> Unit) {
    val set = marks[type] ?: return
    val snapshot = set.toLongArray()
    set.clear()
    for (id in snapshot) action(id)
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
