package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import kotlin.reflect.KClass

/**
 * The narrow, **off-tick-thread** facing view of the [World], injected into everything that
 * touches the ECS from outside the simulation tick: network message handlers, services, item
 * scripts and entity factories.
 *
 * ### Why this exists
 * [World] guards all component access with a single exclusive lock, so any *individual* call is
 * thread-safe. The trap is fetching a live, mutable component and then mutating it **outside** the
 * lock:
 * ```kotlin
 * world.get(id, AvailableSkills::class)?.learnOrUpdate(1, 1) // races the tick thread!
 * ```
 * The read is locked, but `learnOrUpdate` runs unlocked and can corrupt state concurrently ticked
 * by a system. To make that impossible by construction, [WorldView] exposes **no** top-level
 * component accessor (`get`/`add`/`remove`/`destroy`/`markChanged`/`query`/...). The only way to
 * reach a component is through a lock-holding scope ([read]/[modify]/[modifyOrThrow]/[createEntity]),
 * whose block receives the full [World] as its receiver — so everything you do to a component
 * happens while the world lock is held and cannot interleave with the tick.
 *
 * Systems keep receiving the full [World] via `System.update(world, dt)`; they run on the tick
 * thread and legitimately need the open API.
 *
 * This mirrors the contract already documented on [Command]: other threads influence ECS state
 * either synchronously inside a scope block, or asynchronously via [send].
 */
interface WorldView {
  val entityCount: Int

  fun isAlive(id: EntityId): Boolean

  fun hasEntity(id: EntityId): Boolean

  fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean

  /**
   * Runs [block] while holding the world lock. Use for pure reads. **Return values or DTOs — do
   * not leak a component reference out of the block and mutate it later, that reintroduces the very
   * race this type prevents.**
   */
  fun <T> read(block: World.() -> T): T

  /**
   * Runs [block] against [id] while holding the world lock, giving full read+mutate access, or
   * returns null if the entity is not alive.
   */
  fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T?

  /** Like [modify] but throws [EntityNotAliveException] if [id] is not alive. */
  fun <T> modifyOrThrow(id: EntityId, block: World.(EntityId) -> T): T

  /** Atomically creates an entity and configures it (typically a batch of `add`s) under the lock. */
  fun createEntity(configure: World.(EntityId) -> Unit): EntityId

  /**
   * Like [createEntity] but reuses a caller-supplied [id] rather than generating a new one — used
   * when rehydrating a persisted entity so its original (Snowflake) id is preserved. Throws if [id]
   * is already alive.
   */
  fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId

  /** Enqueue external intent from any thread. Applied at the start of the next tick. */
  fun send(command: Command)
}
