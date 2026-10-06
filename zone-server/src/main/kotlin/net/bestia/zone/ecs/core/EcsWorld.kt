package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The engine behind [World] and [WorldView]: entities, component stores, the system scheduler, and the
 * tick itself. Only the engine (`ZoneEngine`, boot runners, tests) holds this type; gameplay code sees
 * [World].
 *
 * ### Tick pipeline (deterministic, single tick thread)
 * ```
 * tick(dt):
 *   1. run posted work          -> tick-lane messages, leases, skill resolutions
 *   2. run due systems          -> scheduler (parallel waves)
 *   3. apply deferred structural changes emitted by systems
 * ```
 *
 * ### Threading
 * The tick thread owns the world and uses its accessors directly. Every other thread goes through a
 * [WorldView] scope, which borrows the world between two ticks, or [post]s work; see [WorldOwnership].
 * Structural changes requested while systems are iterating are deferred to a safe sync point.
 *
 * ### Outbound sync
 * A component knows whether it needs re-sending (see [Dirtyable]). Mutating it through its own setters
 * marks it dirty, which also enters it into [dirtyLog]; the flush visits only those entries.
 */
class EcsWorld(
  parallelSystems: Boolean = false,
  idGenerator: EntityIdGenerator,
  systems: Iterable<System> = emptyList(),
  private val undeclaredAccess: UndeclaredAccess = UndeclaredAccess.OFF,
) : World, WorldView {
  /** What changed since the last sync; see [Dirtyable] and [SpatiallyIndexed]. */
  val dirtyLog = DirtyLog()

  private val entities = EntityRegistry(idGenerator)
  private val stores = ConcurrentHashMap<KClass<out Component>, ComponentStore<out Component>>()
  private val scheduler = SystemScheduler(parallelSystems)
  private val deferred = ConcurrentLinkedQueue<() -> Unit>()

  init {
    scheduler.registerAll(systems)
  }

  private val owner = WorldOwnership()
  private val destroyListeners = CopyOnWriteArrayList<(EntityId) -> Unit>()
  private val componentRemovedListeners = CopyOnWriteArrayList<(EntityId, Component) -> Unit>()

  @Volatile
  private var iterating = false

  override fun <T> read(block: World.() -> T): T = owner.withWorld { this.block() }

  override fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T? = owner.withWorld {
    if (!entities.isAlive(id)) null else this.block(id)
  }

  /** Makes the calling thread the tick thread, which from now on owns the world. */
  fun bindTickThread() {
    owner.bindTickThread()
  }

  fun unbindTickThread() {
    owner.unbindTickThread()
  }

  override fun post(task: World.() -> Unit) {
    owner.post { this.task() }
  }

  /** Runs posted work until [deadlineNanos] ([java.lang.System.nanoTime] scale). Tick thread only. */
  fun runPostedUntil(deadlineNanos: Long) {
    owner.runQueuedUntil(deadlineNanos)
  }

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

  /** Adds [systems] after the ones already registered; the order among them is the order they run in. */
  fun registerSystems(systems: Iterable<System>) {
    scheduler.registerAll(systems)
  }

  /** One line per system, in the order they run. */
  fun describeSystems(): String {
    return scheduler.describe()
  }

  val entityCount: Int get() = entities.count
  val systemCount: Int get() = scheduler.systemCount
  val waveCount: Int get() = scheduler.waveCount

  fun systemStats(): List<SystemStats> {
    return scheduler.stats()
  }

  /** Tick-lane messages, leases and other work waiting for the tick thread. */
  val pendingPosts: Int
    get() {
      return owner.pendingPosts
    }

  /** What the systems on the most recent [tick] cost, slowest first. See [SystemScheduler.lastTickBreakdown]. */
  fun lastTickBreakdown(limit: Int = 5): String = scheduler.lastTickBreakdown(limit)

  // ---------------------------------------------------------------- entities
  override fun isAlive(id: EntityId): Boolean = owner.requireOwned { entities.isAlive(id) }

  override fun createEntity(configure: World.(EntityId) -> Unit): EntityId = owner.withWorld {
    val id = entities.create()
    this.configure(id)
    id
  }

  override fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId = owner.withWorld {
    entities.create(id)
    this.configure(id)
    id
  }

  override fun destroy(id: EntityId) {
    owner.requireOwned {
      if (iterating) deferred.add { destroyNow(id) } else destroyNow(id)
    }
  }

  private fun destroyNow(id: EntityId) {
    if (!entities.destroy(id)) return
    // Notify before wiping: listeners (e.g. the vanish-broadcast hook) need to inspect which
    // components the entity still carries and read its last known state.
    for (listener in destroyListeners) {
      listener(id)
    }
    for (store in stores.values) {
      // Most of the hundred-odd stores are empty or tiny; skipping the empty ones is a size read.
      if (store.size == 0) continue
      @Suppress("UNCHECKED_CAST")
      (store as ComponentStore<Component>).remove(id)
    }
  }

  // -------------------------------------------------------------- components
  @Suppress("UNCHECKED_CAST")
  internal fun <T : Component> store(type: KClass<T>): ComponentStore<T> =
    stores.computeIfAbsent(type) { ComponentStore(type, dirtyLog = dirtyLog) } as ComponentStore<T>

  override fun <T : Component> add(id: EntityId, component: T): T = owner.requireOwned {
    checkDeclared(component::class)
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

  override fun <T : Component> get(id: EntityId, type: KClass<T>): T? = owner.requireOwned {
    checkDeclared(type)
    store(type).get(id)
  }

  override fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean = owner.requireOwned {
    checkDeclared(type)
    store(type).has(id)
  }

  override fun <T : Component> remove(id: EntityId, type: KClass<T>): T? = owner.requireOwned {
    checkDeclared(type)
    if (iterating) {
      deferred.add { removeNow(id, type) }
      null
    } else {
      removeNow(id, type)
    }
  }

  override fun <T : Component> getOrThrow(id: EntityId, type: KClass<T>): T {
    return get(id, type) ?: throw ComponentNotFoundException(id, type)
  }

  private fun <T : Component> removeNow(id: EntityId, type: KClass<T>): T? {
    val removed = store(type).remove(id) ?: return null
    if (entities.isAlive(id)) {
      for (listener in componentRemovedListeners) listener(id, removed)
    }
    return removed
  }

  // ------------------------------------------------------------------ queries
  override fun query(vararg types: KClass<out Component>): Query = owner.requireOwned {
    types.forEach { checkDeclared(it) }
    val byType = LinkedHashMap<KClass<out Component>, ComponentStore<out Component>>(types.size)
    for (type in types) byType[type] = storeErased(type)
    Query(byType, isolateFailures = iterating)
  }

  override fun <T : Component> each(type: KClass<T>, action: (EntityId, T) -> Unit) = owner.requireOwned {
    checkDeclared(type)
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

  private fun checkDeclared(type: KClass<out Component>) {
    if (undeclaredAccess == UndeclaredAccess.OFF) return

    val system = scheduler.runningSystem() ?: return
    if (type in system.reads || type in system.writes) return

    val problem = "${system.name} touched ${type.simpleName} without declaring it in reads or writes"
    when (undeclaredAccess) {
      UndeclaredAccess.FAIL -> throw IllegalStateException(problem)
      else -> undeclaredAccessLog.emit { held -> LOG.warn { "$problem (+$held more)" } }
    }
  }

  private val undeclaredAccessLog = RateLimitedLog()

  // --------------------------------------------------- deferred structural ops
  override fun defer(block: () -> Unit) {
    if (iterating) {
      deferred.add(block)
    } else {
      block()
    }
  }

  private fun applyDeferred() {
    while (true) {
      val job = deferred.poll() ?: break

      try {
        job()
      } catch (e: Throwable) {
        if (e.isFatal()) throw e
        deferredFailureLog.emit { held -> LOG.error(e) { "A deferred change failed, the rest still apply (+$held more)" } }
      }
    }
  }

  private val deferredFailureLog = RateLimitedLog()

  override var tickCount: Long = 0L
    private set

  // -------------------------------------------------------------- tick pipeline
  fun tick(deltaTime: Float) = owner.withWorld {
    owner.runQueued()        // posted work: tick-lane messages, leases, skill resolutions
    tickCount++
    iterating = true
    try {
      scheduler.tick(this, deltaTime) // due systems (parallel waves)
    } finally {
      iterating = false
    }
    applyDeferred()          // structural changes emitted by systems
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
