package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId

import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass

/**
 * Typed, allocation-conscious joins over an arbitrary number of component
 * stores, built via [World.query]. Multi-store queries iterate the *smallest*
 * store and resolve the remaining components by id, skipping entities that
 * do not have all of them. This keeps iteration proportional to the rarest
 * component rather than the whole world.
 *
 * Component values inside [each] are read via [Row.get], e.g.
 * `world.query(Position::class, Speed::class).each { id -> val p = get<Position>() }`.
 *
 * [each] allocates a single [Row] for the whole call (not per entity), mutated
 * in place per matching entity.
 *
 * With [isolateFailures] (set while a tick runs) an entity whose action throws is logged and
 * skipped, so one bad entity does not end its system's whole update.
 */
class Query internal constructor(
  private val stores: Map<KClass<out Component>, ComponentStore<out Component>>,
  private val isolateFailures: Boolean = false,
) {
  init {
    require(stores.isNotEmpty()) { "World.query() requires at least one component type" }
  }

  private val storeList: List<ComponentStore<out Component>> = stores.values.toList()
  private val storeArray: Array<ComponentStore<out Component>> = storeList.toTypedArray()

  private fun driver(): ComponentStore<out Component> =
    storeList.reduce { smallest, s -> if (s.size < smallest.size) s else smallest }

  private fun matchesAll(driver: ComponentStore<out Component>, id: EntityId): Boolean {
    for (s in storeList) if (s !== driver && !s.has(id)) return false
    return true
  }

  fun each(action: Row.(EntityId) -> Unit) {
    val driver = driver()
    val row = Row(storeArray)
    val failures = AtomicInteger()
    for (i in 0 until driver.size) {
      val id = driver.entityAt(i)
      if (!matchesAll(driver, id)) continue
      row.currentId = id
      runIsolated(id, failures) { row.action(id) }
    }
  }

  /**
   * More than [MAX_FAILURES_PER_PASS] failures in one pass is a bug in the system rather than in one
   * entity, so the failure is rethrown and the scheduler counts it against the system.
   */
  private inline fun runIsolated(id: EntityId, failures: AtomicInteger, action: () -> Unit) {
    if (!isolateFailures) {
      action()
      return
    }

    try {
      action()
    } catch (e: Throwable) {
      if (e.isFatal() || failures.incrementAndGet() > MAX_FAILURES_PER_PASS) throw e
      FAILURE_LOG.emit { held -> LOG.error(e) { "Entity $id failed in a query and was skipped (+$held more)" } }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
    private val FAILURE_LOG = RateLimitedLog()

    const val MAX_FAILURES_PER_PASS = 8
  }
}

/**
 * Scoped accessor for the "current" joined entity inside a [Query.each] callback. A `Row` instance is
 * reused across entities — never store `this` or a `Row` reference outside the lambda body.
 */
class Row internal constructor(
  @PublishedApi internal val stores: Array<ComponentStore<out Component>>,
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
    // A query joins a handful of types, so comparing class references beats hashing a KClass on every read.
    val type = T::class.java
    for (store in stores) {
      if (store.javaType !== type) continue

      @Suppress("UNCHECKED_CAST")
      return (store as ComponentStore<T>).get(currentId)
        ?: error(
          "Row.get<${type.simpleName}>() found nothing for entity $currentId despite the join match " +
            "— bug in Query's join logic."
        )
    }

    throw IllegalStateException(
      "Row.get<${type.simpleName}>(): ${type.simpleName} is not part of this query's " +
        "component types (${stores.map { it.type.simpleName }}); add it to the world.query(...) call."
    )
  }
}
