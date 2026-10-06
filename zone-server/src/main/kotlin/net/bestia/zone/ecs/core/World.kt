package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import kotlin.reflect.KClass

/**
 * The entities and components, as gameplay code sees them: systems, tick-lane handlers, and the block of a
 * [WorldView] scope. Only the thread that has the world may call it, see [WorldOwnership]; the engine side
 * (ticking, binding, listeners) is [EcsWorld].
 */
interface World {
  /** Ticks since the world was created, for "not before a while from now" without a wall clock. */
  val tickCount: Long

  fun isAlive(id: EntityId): Boolean

  /** Creates an entity and runs [configure] on it, typically a batch of [add]s. */
  fun createEntity(configure: World.(EntityId) -> Unit): EntityId

  /** Like [createEntity], but keeps a persisted entity's id. Throws if [id] is already alive. */
  fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId

  /** Deferred while systems iterate, like [add] and [remove]. */
  fun destroy(id: EntityId)

  fun <T : Component> get(id: EntityId, type: KClass<T>): T?

  fun <T : Component> getOrThrow(id: EntityId, type: KClass<T>): T

  fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean

  /** A freshly created component starts dirty (see [DirtyTracked]), so adding one already queues it for sync. */
  fun <T : Component> add(id: EntityId, component: T): T

  /** Returns null while systems iterate, because the removal is deferred. */
  fun <T : Component> remove(id: EntityId, type: KClass<T>): T?

  fun query(vararg types: KClass<out Component>): Query

  /** Visits every `(entity, component)` pair currently stored for [type]. */
  fun <T : Component> each(type: KClass<T>, action: (EntityId, T) -> Unit)

  /** Runs [block] now, or at the next safe sync point if systems are iterating. */
  fun defer(block: () -> Unit)

  /** Runs [task] on the tick thread between two ticks, after the current tick and its sync have finished. */
  fun post(task: World.() -> Unit)
}

/** Runs [block] against [id] if it is alive, or returns null. The tick-side twin of [WorldView.modify]. */
inline fun <T> World.modify(id: EntityId, block: World.(EntityId) -> T): T? {
  return if (isAlive(id)) block(id) else null
}

inline fun <reified T : Component> World.getOrThrow(id: EntityId): T {
  return getOrThrow(id, T::class)
}

/**
 * Gets [T] on [id], creating it with [default] if missing, then mutates it in [block]. The component's own
 * setters mark it dirty, so no change flag is needed.
 */
inline fun <reified T : Component> World.update(id: EntityId, default: () -> T, block: (T) -> Unit) {
  if (!isAlive(id)) {
    return
  }

  val component = get(id, T::class) ?: add(id, default())
  block(component)
}
