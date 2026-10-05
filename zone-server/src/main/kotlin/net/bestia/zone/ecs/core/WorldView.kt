package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import kotlin.reflect.KClass

/**
 * The narrow, **off-tick-thread** facing view of the [World], injected into everything that touches the ECS
 * from outside the simulation tick: message handlers, services, item scripts and entity spawners.
 *
 * It exposes no top-level component accessor. A component is only reachable inside a scope
 * ([read]/[modify]/[modifyOrThrow]/[createEntity]) that has the world to itself - inline on the tick
 * thread, on a lease anywhere else (see [WorldOwnership]) - so a component cannot be mutated while the tick
 * runs. Work that should simply happen on the tick goes through [post].
 */
interface WorldView {
  val entityCount: Int

  fun isAlive(id: EntityId): Boolean

  fun hasEntity(id: EntityId): Boolean

  fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean

  /**
   * Runs [block] with the world to itself. Use for pure reads. **Return values or DTOs — do not leak a
   * component reference out of the block and mutate it later, that reintroduces the very race this type
   * prevents.**
   */
  fun <T> read(block: World.() -> T): T

  /**
   * Runs [block] against [id] with the world to itself, giving full read+mutate access, or returns null
   * if the entity is not alive.
   */
  fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T?

  /** Like [modify] but throws [EntityNotAliveException] if [id] is not alive. */
  fun <T> modifyOrThrow(id: EntityId, block: World.(EntityId) -> T): T

  /** Atomically creates an entity and configures it (typically a batch of `add`s). */
  fun createEntity(configure: World.(EntityId) -> Unit): EntityId

  /**
   * Like [createEntity] but reuses a caller-supplied [id] rather than generating a new one — used
   * when rehydrating a persisted entity so its original (Snowflake) id is preserved. Throws if [id]
   * is already alive.
   */
  fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId

  /** Enqueue external intent from any thread. Applied at the start of the next tick. */
  fun send(command: Command)

  /** Runs [task] on the tick thread: between two ticks, or at the start of the next one. From any thread. */
  fun post(task: World.() -> Unit)
}
