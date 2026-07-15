package net.bestia.zone.ecs.core

import net.bestia.zone.util.EntityId

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.reflect.KClass

/**
 * The central ECS facade. Owns entities, component stores, the system scheduler,
 * and the inbound/outbound messaging queues. Everything gameplay-related flows
 * through here.
 *
 * ### Tick pipeline (deterministic, single tick thread)
 * ```
 * tick(dt):
 *   1. apply deferred structural changes queued last tick
 *   2. drain external commands  -> onCommand handlers
 *   3. run due systems          -> scheduler (parallel waves)
 *   4. apply deferred structural changes emitted by systems
 * ```
 *
 * ### Threading
 * Only the tick thread mutates ECS state. Other threads may [send] commands
 * (thread-safe). Structural changes requested while systems are iterating are
 * automatically deferred to a safe sync point.
 *
 * ### Outbound sync
 * The world keeps no separate "changed" bookkeeping: a component is the single source of
 * truth for whether it needs re-sending (see [net.bestia.zone.ecs.Dirtyable]). Mutating a
 * component through its own setters marks it dirty; the flush scans stores via [each] and
 * sends whatever reports dirty.
 */
class World(
  parallelSystems: Boolean = false,
  idGenerator: EntityIdGenerator,
  systems: Iterable<System>
) : WorldView {
  private val entities = EntityRegistry(idGenerator)
  private val stores = ConcurrentHashMap<KClass<out Component>, ComponentStore<out Component>>()
  private val scheduler = SystemScheduler(parallelSystems)
  private val commands = CommandQueue()
  private val deferred = ConcurrentLinkedQueue<() -> Unit>()

  init {
    scheduler.registerAll(systems)
  }

  /**
   * Guards all structural changes, component access and the tick against concurrent access from
   * non-tick threads (network handlers, factories, ...). The lock is reentrant so systems running
   * inside [tick] (which already holds it) may freely call [get]/[add]/... The old lock-per-entity
   * model of `EntityManager` is replaced by this single coarse lock; only meaningful when
   * `parallelSystems` is disabled (the default), which mirrors the previous single-threaded loop.
   */
  private val lock = ReentrantLock()
  private val destroyListeners = CopyOnWriteArrayList<(EntityId) -> Unit>()
  private val componentRemovedListeners = CopyOnWriteArrayList<(EntityId, Component) -> Unit>()

  @Volatile
  private var iterating = false

  /** Runs [block] holding the world lock; used to make external ECS access thread-safe. */
  fun <T> locked(block: () -> T): T = lock.withLock(block)

  /**
   * [WorldView] read scope: runs [block] against this world while holding the lock. Intended for
   * pure reads from off-tick threads; return values/DTOs rather than leaking components out.
   */
  override fun <T> read(block: World.() -> T): T = lock.withLock { this.block() }

  /** Registers a hook fired (on the tick thread) whenever an entity is destroyed. */
  fun onDestroy(handler: (EntityId) -> Unit) {
    destroyListeners.add(handler)
  }

  /**
   * Registers a hook fired whenever a single component is *explicitly* removed from a still-alive
   * entity (via [remove]), receiving the removed instance. It deliberately does NOT fire when a
   * whole entity is destroyed ([destroy] wipes stores directly) — that case is a vanish, not a
   * per-component removal. Used by the sync layer to notify clients of component removals without
   * the ECS core needing to know anything about the wire format.
   */
  fun onComponentRemoved(handler: (EntityId, Component) -> Unit) {
    componentRemovedListeners.add(handler)
  }

  override val entityCount: Int get() = entities.count
  val systemCount: Int get() = scheduler.systemCount
  val waveCount: Int get() = scheduler.waveCount

  // ---------------------------------------------------------------- entities
  fun create(): EntityId = lock.withLock { entities.create() }

  fun create(id: EntityId): EntityId = lock.withLock { entities.create(id) }

  override fun isAlive(id: EntityId): Boolean = lock.withLock { entities.isAlive(id) }

  /** Alias for [isAlive] preserving the previous `ZoneServer.hasEntity` naming. */
  override fun hasEntity(id: EntityId): Boolean = isAlive(id)

  /**
   * Atomically creates an entity and runs [configure] on it (typically a batch of [add]s) while
   * holding the world lock, then returns the new id. Replaces `ZoneServer.addEntityWithWriteLock`.
   */
  override fun createEntity(configure: World.(EntityId) -> Unit): EntityId = lock.withLock {
    val id = entities.create()
    this.configure(id)
    id
  }

  override fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId = lock.withLock {
    entities.create(id)
    this.configure(id)
    id
  }

  /**
   * Runs [block] against [id] while holding the world lock, or returns null if the entity is not
   * alive. Replaces `ZoneServer.withEntityWriteLock` / `withEntityReadLock` (a single tick thread
   * makes read/write locks unnecessary).
   */
  override fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T? = lock.withLock {
    if (!entities.isAlive(id)) null else this.block(id)
  }

  /** Like [modify] but throws [EntityNotAliveException] if [id] is not alive. */
  override fun <T> modifyOrThrow(id: EntityId, block: World.(EntityId) -> T): T =
    modify(id, block) ?: throw EntityNotAliveException(id)

  fun destroy(id: EntityId) = lock.withLock {
    if (iterating) deferred.add { destroyNow(id) } else destroyNow(id)
  }

  private fun destroyNow(id: EntityId) {
    if (!entities.destroy(id)) return
    for (store in stores.values) {
      @Suppress("UNCHECKED_CAST")
      (store as ComponentStore<Component>).remove(id)
    }
    for (listener in destroyListeners) listener(id)
  }

  // -------------------------------------------------------------- components
  @Suppress("UNCHECKED_CAST")
  fun <T : Component> store(type: KClass<T>): ComponentStore<T> =
    stores.computeIfAbsent(type) { ComponentStore(type) } as ComponentStore<T>

  /** Enables object pooling (see [ComponentType]) for a component type. */
  fun <T : Component> registerPooled(componentType: ComponentType<T>) {
    stores[componentType.type] = ComponentStore(componentType.type, componentType.factory, componentType.reset)
  }

  /**
   * Adds a component to [id]. Deferred if called mid-tick. A freshly created component starts
   * dirty (see [net.bestia.zone.ecs.Dirtyable]), so adding one already queues it for sync.
   */
  fun <T : Component> add(id: EntityId, component: T): T = lock.withLock {
    if (iterating) {
      deferred.add {
        addNow(id, component)
      }
    } else {
      addNow(id, component)
    }
    component
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Component> addNow(id: EntityId, component: T) {
    require(entities.isAlive(id)) { "Cannot add component to dead entity $id" }
    store(component::class as KClass<T>).set(id, component)
  }

  fun <T : Component> get(id: EntityId, type: KClass<T>): T? = lock.withLock { store(type).get(id) }

  override fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean = lock.withLock { store(type).has(id) }

  fun <T : Component> remove(id: EntityId, type: KClass<T>): T? = lock.withLock {
    if (iterating) {
      deferred.add { removeNow(id, type) }
      null
    } else {
      removeNow(id, type)
    }
  }

  /** Convenience: fetch a component, throwing if the entity does not have it. */
  fun <T : Component> getOrThrow(id: EntityId, type: KClass<T>): T =
    get(id, type) ?: throw ComponentNotFoundException(id, type)

  inline fun <reified T : Component> getOrThrow(id: EntityId): T = getOrThrow(id, T::class)

  /**
   * Convenience for the common "fetch [T] on [id], creating it via [default] if missing, then
   * mutate it" pattern seen across systems (e.g. `get(id, Exp::class) ?: add(id, Exp())`). The
   * mutation in [block] marks the component dirty through its own setters (see
   * [net.bestia.zone.ecs.Dirtyable]), and a freshly created default starts dirty, so no explicit
   * change flag is needed.
   */
  inline fun <reified T : Component> update(id: EntityId, default: () -> T, block: (T) -> Unit) {
    if (!isAlive(id)) {
      return
    }

    val component = get(id, T::class) ?: add(id, default())
    block(component)
  }

  private fun <T : Component> removeNow(id: EntityId, type: KClass<T>): T? {
    val removed = store(type).remove(id) ?: return null
    if (entities.isAlive(id)) {
      for (listener in componentRemovedListeners) listener(id, removed)
    }
    return removed
  }

  // ------------------------------------------------------------------ queries
  fun query(vararg types: KClass<out Component>): Query {
    val byType = LinkedHashMap<KClass<out Component>, ComponentStore<out Component>>(types.size)
    for (type in types) byType[type] = storeErased(type)
    return Query(byType)
  }

  /** Visits every `(entity, component)` pair currently stored for [type]. */
  fun <T : Component> each(type: KClass<T>, action: (EntityId, T) -> Unit) = lock.withLock {
    store(type).each(action)
  }

  /**
   * Resolves a store for an erased `KClass<out Component>` element from [query]'s vararg. The
   * cast is safe: [store] only uses the KClass as a map key and constructor argument, never to
   * enforce `T` at runtime — the same pattern already used in [addNow].
   */
  @Suppress("UNCHECKED_CAST")
  private fun storeErased(type: KClass<out Component>): ComponentStore<out Component> =
    store(type as KClass<Component>)

  // ------------------------------------------------------------- messaging in
  /** Enqueue external intent from any thread. Applied at the start of next tick. */
  override fun send(command: Command) {
    commands.enqueue(command)
  }

  fun <T : Command> onCommand(type: KClass<T>, handler: (World, T) -> Unit) {
    commands.on(type, handler)
  }

  inline fun <reified T : Command> onCommand(noinline handler: (World, T) -> Unit) {
    onCommand(T::class, handler)
  }

  // --------------------------------------------------- deferred structural ops
  /** Run [block] now, or defer it to the next safe sync point if mid-tick. */
  fun defer(block: () -> Unit) {
    if (iterating) {
      deferred.add(block)
    } else {
      block()
    }
  }

  private fun applyDeferred() {
    while (true) {
      val job = deferred.poll() ?: break
      job()
    }
  }

  // -------------------------------------------------------------- tick pipeline
  fun tick(deltaTime: Float) = lock.withLock {
    commands.drain(this)     // external intent -> handlers
    iterating = true
    try {
      scheduler.tick(this, deltaTime) // due systems (parallel waves)
    } finally {
      iterating = false
    }
    applyDeferred()          // structural changes emitted by systems
  }
}
