package net.bestia.zone.ecs.core

import java.util.stream.IntStream
import kotlin.reflect.KClass

/**
 * Typed, allocation-conscious joins over an arbitrary number of component
 * stores, built via [World.query]. Multi-store queries iterate the *smallest*
 * store and resolve the remaining components by id, skipping entities that
 * do not have all of them. This keeps iteration proportional to the rarest
 * component rather than the whole world.
 *
 * Component values inside [each]/[parallelEach] are read via [Row.get], e.g.
 * `world.query(Position::class, Speed::class).each { id -> val p = get<Position>() }`.
 *
 * [each] allocates a single [Row] for the whole call (not per entity), mutated
 * in place per matching entity. [parallelEach] allocates one [Row] per
 * fork-join worker thread touched by the call (via a call-scoped
 * `ThreadLocal`), since a single shared `Row` would race across threads.
 *
 * [parallelEach] splits the driving store's dense range across the common
 * fork-join pool. It must only be used by systems that do not perform structural
 * changes on the involved stores during iteration (mutating existing component
 * fields and calling [World.markChanged] are both safe).
 */
class Query internal constructor(
  private val stores: Map<KClass<out Component>, ComponentStore<out Component>>,
) {
  init {
    require(stores.isNotEmpty()) { "World.query() requires at least one component type" }
  }

  private val storeList: List<ComponentStore<out Component>> = stores.values.toList()

  private fun driver(): ComponentStore<out Component> =
    storeList.reduce { smallest, s -> if (s.size < smallest.size) s else smallest }

  private fun matchesAll(driver: ComponentStore<out Component>, id: EntityId): Boolean {
    for (s in storeList) if (s !== driver && !s.has(id)) return false
    return true
  }

  fun each(action: Row.(EntityId) -> Unit) {
    val driver = driver()
    val row = Row(stores)
    for (i in 0 until driver.size) {
      val id = driver.entityAt(i)
      if (!matchesAll(driver, id)) continue
      row.currentId = id
      row.action(id)
    }
  }

  fun parallelEach(action: Row.(EntityId) -> Unit) {
    val driver = driver()
    val threadRow = ThreadLocal.withInitial { Row(stores) }
    IntStream.range(0, driver.size).parallel().forEach { i ->
      val id = driver.entityAt(i)
      if (!matchesAll(driver, id)) return@forEach
      val row = threadRow.get()
      row.currentId = id
      row.action(id)
    }
  }
}

/**
 * Scoped accessor for the "current" joined entity inside a [Query.each] /
 * [Query.parallelEach] callback. A `Row` instance is reused across entities
 * (and, for [Query.parallelEach], shared only within one worker thread) — never
 * store `this` or a `Row` reference outside the lambda body.
 */
class Row internal constructor(
  @PublishedApi internal val stores: Map<KClass<out Component>, ComponentStore<out Component>>,
) {
  @PublishedApi
  internal var currentId: EntityId = -1L

  /**
   * Returns the [T] component for the current entity. Throws
   * [IllegalStateException] if [T] was not one of the types passed to
   * [World.query] for this query (a caller bug, not a missing-component
   * case — join membership already guarantees the component is present).
   */
  inline fun <reified T : Component> get(): T {
    val store = stores[T::class]
      ?: throw IllegalStateException(
        "Row.get<${T::class.simpleName}>(): ${T::class.simpleName} is not part of this query's " +
          "component types (${stores.keys.map { it.simpleName }}); add it to the world.query(...) call."
      )
    @Suppress("UNCHECKED_CAST")
    return (store as ComponentStore<T>).get(currentId)
      ?: error(
        "Row.get<${T::class.simpleName}>() found nothing for entity $currentId despite the join match " +
          "— bug in Query's join logic."
      )
  }
}
