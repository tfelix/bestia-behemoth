package net.bestia.zone.ecs.core

import java.util.concurrent.atomic.AtomicLong

/**
 * Tracks entity liveness. Ids are opaque longs and may either be generated here
 * or supplied by the caller (e.g. an existing snowflake generator) for interop
 * with the current entity id space.
 *
 * Not thread-safe; all structural changes run on the tick thread (deferred by
 * [World] when they happen mid-iteration).
 */
class EntityRegistry(
  private val idGenerator: () -> EntityId = defaultGenerator(),
) {
  private val alive = Long2IntOpenHashMap()

  var count: Int = 0
    private set

  /** Creates a new entity using the configured generator. */
  fun create(): EntityId {
    val id = idGenerator()
    register(id)
    return id
  }

  /** Registers an externally-generated id (e.g. a snowflake id). */
  fun create(id: EntityId): EntityId {
    register(id)
    return id
  }

  private fun register(id: EntityId) {
    require(alive.put(id, ALIVE) == Long2IntOpenHashMap.ABSENT) { "Entity $id already exists" }
    count++
  }

  fun isAlive(id: EntityId): Boolean = alive.containsKey(id)

  /** Returns true if the entity existed and was removed. */
  fun destroy(id: EntityId): Boolean {
    if (alive.remove(id) == Long2IntOpenHashMap.ABSENT) return false
    count--
    return true
  }

  companion object {
    private const val ALIVE = 1

    /** Simple monotonic generator; replace with a snowflake generator for interop. */
    fun defaultGenerator(): () -> EntityId {
      val counter = AtomicLong(1L)
      return { counter.getAndIncrement() }
    }
  }
}
