package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import kotlin.reflect.KClass

/**
 * Sparse-set storage for a single component type (EnTT style).
 *
 * ```
 * sparse:     Long2IntOpenHashMap   // entityId -> dense slot
 * entities:   LongArray             // dense slot -> entityId   (parallel)
 * components: Array<T?>            // dense slot -> component   (parallel, contiguous)
 * ```
 *
 * Iteration walks the contiguous [components]/[entities] arrays for cache
 * efficiency. Removal is a swap-remove (move the last element into the hole) so
 * the dense range stays packed and add/remove stay O(1). When a pool is
 * configured, removed instances are recycled to spare the GC.
 *
 * Not thread-safe for structural changes. Within a scheduler wave a given
 * component type is written by at most one system (see [SystemScheduler]), so
 * concurrent access to one store never races.
 */
class ComponentStore<T : Component>(
  val type: KClass<T>,
  private val factory: (() -> T)? = null,
  private val reset: ((T) -> Unit)? = null,
  initialCapacity: Int = 64,
) {
  private val sparse = Long2IntOpenHashMap(initialCapacity)
  private var entities = LongArray(initialCapacity)

  @Suppress("UNCHECKED_CAST")
  private var components = arrayOfNulls<Component>(initialCapacity) as Array<T?>

  private var count = 0
  private val pool: ArrayDeque<T>? = if (factory != null) ArrayDeque() else null

  val size: Int get() = count

  fun has(entity: EntityId): Boolean = sparse.containsKey(entity)

  fun get(entity: EntityId): T? {
    val i = sparse.get(entity)
    return if (i == Long2IntOpenHashMap.ABSENT) null else components[i]
  }

  /** Adds or replaces the component instance for [entity]. */
  fun set(entity: EntityId, component: T) {
    val existing = sparse.get(entity)
    if (existing != Long2IntOpenHashMap.ABSENT) {
      components[existing] = component
      return
    }
    if (count == entities.size) grow()
    entities[count] = entity
    components[count] = component
    sparse.put(entity, count)
    count++
  }

  /**
   * Obtains a (possibly recycled) instance, attaches it to [entity] and returns
   * it for in-place mutation. Requires the store to have been created with a
   * [factory] (see [World.registerPooled]).
   */
  fun obtain(entity: EntityId): T {
    val f = factory ?: error("ComponentStore<${type.simpleName}> has no factory; use set() instead")
    val instance = pool?.removeLastOrNull() ?: f()
    set(entity, instance)
    return instance
  }

  /** Removes the component via swap-remove; recycles the instance if pooled. */
  fun remove(entity: EntityId): T? {
    val i = sparse.get(entity)
    if (i == Long2IntOpenHashMap.ABSENT) return null
    val removed = components[i]
    val last = count - 1
    if (i != last) {
      val movedEntity = entities[last]
      entities[i] = movedEntity
      components[i] = components[last]
      sparse.put(movedEntity, i)
    }
    entities[last] = 0L
    components[last] = null
    sparse.remove(entity)
    count--

    if (removed != null && pool != null) {
      reset?.invoke(removed)
      pool.addLast(removed)
    }
    return removed
  }

  /** Contiguous iteration over all (entity, component) pairs in this store. */
  fun each(action: (EntityId, T) -> Unit) {
    for (i in 0 until count) {
      action(entities[i], componentAt(i))
    }
  }

  // --- dense accessors used by Query for join iteration / parallel partitioning
  fun entityAt(index: Int): EntityId = entities[index]

  @Suppress("UNCHECKED_CAST")
  fun componentAt(index: Int): T = components[index] as T

  private fun grow() {
    val newCap = entities.size * 2
    entities = entities.copyOf(newCap)
    components = components.copyOf(newCap)
  }
}
