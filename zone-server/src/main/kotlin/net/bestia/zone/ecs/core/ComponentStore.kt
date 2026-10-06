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
 * the dense range stays packed and add/remove stay O(1).
 *
 * Not thread-safe for structural changes. Within a scheduler wave a given
 * component type is written by at most one system (see [SystemScheduler]), so
 * concurrent access to one store never races.
 */
class ComponentStore<T : Component>(
  val type: KClass<T>,
  initialCapacity: Int = 64,
  private val dirtyLog: DirtyLog? = null,
) {
  private val dirtySink: DirtyFlag.Sink? = dirtyLog?.sinkFor(type)

  /** For [Row.get], which finds its store by comparing these. */
  @PublishedApi
  internal val javaType: Class<T> = type.java

  /** The store never shrinks below where it started, so a small store does not churn. */
  private val minCapacity = initialCapacity

  private val sparse = Long2IntOpenHashMap(initialCapacity)
  private var entities = LongArray(initialCapacity)

  @Suppress("UNCHECKED_CAST")
  private var components = arrayOfNulls<Component>(initialCapacity) as Array<T?>

  private var count = 0

  val size: Int get() = count

  internal val capacity: Int get() = entities.size

  fun has(entity: EntityId): Boolean {
    return sparse.containsKey(entity)
  }

  fun get(entity: EntityId): T? {
    val i = sparse.get(entity)
    return if (i == Long2IntOpenHashMap.ABSENT) null else components[i]
  }

  /** Adds or replaces the component instance for [entity]. */
  fun set(entity: EntityId, component: T) {
    val existing = sparse.get(entity)
    if (existing != Long2IntOpenHashMap.ABSENT) {
      components[existing]?.let { untrack(entity, it) }
      components[existing] = component
      track(entity, component)
      return
    }

    if (count == entities.size) {
      grow()
    }

    entities[count] = entity
    components[count] = component
    sparse.put(entity, count)
    count++
    track(entity, component)
  }

  private fun track(entity: EntityId, component: T) {
    if (component is DirtyTracked && dirtySink != null) component.dirtyFlag.attach(entity, dirtySink)
    if (component is SpatiallyIndexed && dirtyLog != null) component.movedFlag.attach(entity, dirtyLog.movedSink)
  }

  private fun untrack(entity: EntityId, component: T) {
    if (component is DirtyTracked) component.dirtyFlag.detachFrom(entity)
    if (component is SpatiallyIndexed) component.movedFlag.detachFrom(entity)
  }

  /** Removes the component via swap-remove. */
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

    removed?.let { untrack(entity, it) }

    // A crowd that left (a town emptied at night, a battle over) would otherwise pin its arrays forever.
    if (entities.size > minCapacity && count < entities.size / 4) shrink()
    return removed
  }

  /** Contiguous iteration over all (entity, component) pairs in this store. */
  fun each(action: (EntityId, T) -> Unit) {
    for (i in 0 until count) {
      action(entities[i], componentAt(i))
    }
  }

  // --- dense accessors used by Query for join iteration
  fun entityAt(index: Int): EntityId = entities[index]

  @Suppress("UNCHECKED_CAST")
  fun componentAt(index: Int): T = components[index] as T

  private fun grow() {
    val newCap = entities.size * 2
    entities = entities.copyOf(newCap)
    components = components.copyOf(newCap)
  }

  /** Halves the arrays; at a quarter full there is room for the store to double again before it grows. */
  private fun shrink() {
    val newCap = maxOf(entities.size / 2, minCapacity)
    entities = entities.copyOf(newCap)
    components = components.copyOf(newCap)
  }
}
