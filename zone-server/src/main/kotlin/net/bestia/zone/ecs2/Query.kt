package net.bestia.zone.ecs2

import java.util.stream.IntStream

/**
 * Typed, allocation-free-ish joins over one to three component stores.
 *
 * Multi-store queries iterate the *smallest* store and resolve the remaining
 * components by id, skipping entities that do not have all of them. This keeps
 * iteration proportional to the rarest component rather than the whole world.
 *
 * [parallelEach] splits the driving store's dense range across the common
 * fork-join pool. It must only be used by systems that do not perform structural
 * changes on the involved stores during iteration (mutating existing component
 * fields and calling [World.markChanged] are both safe).
 */
class Query1<A : Component>(private val a: ComponentStore<A>) {
  fun each(action: (EntityId, A) -> Unit) {
    for (i in 0 until a.size) action(a.entityAt(i), a.componentAt(i))
  }

  fun parallelEach(action: (EntityId, A) -> Unit) {
    IntStream.range(0, a.size).parallel().forEach { i ->
      action(a.entityAt(i), a.componentAt(i))
    }
  }
}

class Query2<A : Component, B : Component>(
  private val a: ComponentStore<A>,
  private val b: ComponentStore<B>,
) {
  fun each(action: (EntityId, A, B) -> Unit) {
    if (a.size <= b.size) {
      for (i in 0 until a.size) {
        val id = a.entityAt(i)
        val bc = b.get(id) ?: continue
        action(id, a.componentAt(i), bc)
      }
    } else {
      for (i in 0 until b.size) {
        val id = b.entityAt(i)
        val ac = a.get(id) ?: continue
        action(id, ac, b.componentAt(i))
      }
    }
  }

  fun parallelEach(action: (EntityId, A, B) -> Unit) {
    val driver = if (a.size <= b.size) a else b
    IntStream.range(0, driver.size).parallel().forEach { i ->
      val id = driver.entityAt(i)
      val ac = a.get(id) ?: return@forEach
      val bc = b.get(id) ?: return@forEach
      action(id, ac, bc)
    }
  }
}

class Query3<A : Component, B : Component, C : Component>(
  private val a: ComponentStore<A>,
  private val b: ComponentStore<B>,
  private val c: ComponentStore<C>,
) {
  private fun smallest(): ComponentStore<*> {
    var s: ComponentStore<*> = a
    if (b.size < s.size) s = b
    if (c.size < s.size) s = c
    return s
  }

  fun each(action: (EntityId, A, B, C) -> Unit) {
    val driver = smallest()
    for (i in 0 until driver.size) {
      val id = driver.entityAt(i)
      val ac = a.get(id) ?: continue
      val bc = b.get(id) ?: continue
      val cc = c.get(id) ?: continue
      action(id, ac, bc, cc)
    }
  }

  fun parallelEach(action: (EntityId, A, B, C) -> Unit) {
    val driver = smallest()
    IntStream.range(0, driver.size).parallel().forEach { i ->
      val id = driver.entityAt(i)
      val ac = a.get(id) ?: return@forEach
      val bc = b.get(id) ?: return@forEach
      val cc = c.get(id) ?: return@forEach
      action(id, ac, bc, cc)
    }
  }
}
