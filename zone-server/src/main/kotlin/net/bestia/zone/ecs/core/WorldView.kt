package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

/**
 * The door to the [World] for every thread but the tick thread: message handlers on the IO lane, services,
 * schedulers and DB jobs.
 *
 * It has no accessor of its own. A scope ([read]/[modify]/[createEntity]) has the world to itself - inline
 * on the tick thread, on a lease anywhere else (see [WorldOwnership]) - so a component cannot change while
 * the tick runs. Work that should simply happen on the tick goes through [post].
 */
interface WorldView {
  /**
   * Runs [block] with the world to itself. **Return values or DTOs — do not leak a component reference out
   * of the block and use it later, that reintroduces the very race this type prevents.**
   */
  fun <T> read(block: World.() -> T): T

  /** Runs [block] against [id] with the world to itself, or returns null if the entity is not alive. */
  fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T?

  /** Creates an entity and runs [configure] on it, typically a batch of `add`s. */
  fun createEntity(configure: World.(EntityId) -> Unit): EntityId

  /** Like [createEntity], but keeps a persisted entity's id. Throws if [id] is already alive. */
  fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId

  /** Runs [task] on the tick thread: between two ticks, or at the start of the next one. From any thread. */
  fun post(task: World.() -> Unit)
}
