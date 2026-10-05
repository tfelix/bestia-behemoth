package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The central ECS facade. Owns entities, component stores and the system scheduler. Everything
 * gameplay-related flows through here.
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
class World(
  parallelSystems: Boolean = false,
  idGenerator: EntityIdGenerator,
  systems: Iterable<System> = emptyList(),
) : WorldView {
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

  val entityCount: Int get() = entities.count
  val systemCount: Int get() = scheduler.systemCount
  val waveCount: Int get() = scheduler.waveCount

  /** What the systems on the most recent [tick] cost, slowest first. See [SystemScheduler.lastTickBreakdown]. */
  fun lastTickBreakdown(limit: Int = 5): String = scheduler.lastTickBreakdown(limit)

  // ---------------------------------------------------------------- entities
  fun isAlive(id: EntityId): Boolean = owner.requireOwned { entities.isAlive(id) }

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

  fun destroy(id: EntityId) = owner.requireOwned {
    if (iterating) deferred.add { destroyNow(id) } else destroyNow(id)
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
  fun <T : Component> store(type: KClass<T>): ComponentStore<T> =
    stores.computeIfAbsent(type) { ComponentStore(type, dirtyLog = dirtyLog) } as ComponentStore<T>

  /**
   * Adds a component to [id]. Deferred if called mid-tick. A freshly created component starts
   * dirty (see [Dirtyable]), so adding one already queues it for sync.
   */
  fun <T : Component> add(id: EntityId, component: T): T = owner.requireOwned {
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

  fun <T : Component> get(id: EntityId, type: KClass<T>): T? = owner.requireOwned { store(type).get(id) }

  fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean = owner.requireOwned { store(type).has(id) }

  fun <T : Component> remove(id: EntityId, type: KClass<T>): T? = owner.requireOwned {
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
   * [Dirtyable]), and a freshly created default starts dirty, so no explicit
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
  fun query(vararg types: KClass<out Component>): Query = owner.requireOwned {
    val byType = LinkedHashMap<KClass<out Component>, ComponentStore<out Component>>(types.size)
    for (type in types) byType[type] = storeErased(type)
    Query(byType, isolateFailures = iterating)
  }

  /** Visits every `(entity, component)` pair currently stored for [type]. */
  fun <T : Component> each(type: KClass<T>, action: (EntityId, T) -> Unit) = owner.requireOwned {
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

      try {
        job()
      } catch (e: Throwable) {
        if (e.isFatal()) throw e
        deferredFailureLog.emit { held -> LOG.error(e) { "A deferred change failed, the rest still apply (+$held more)" } }
      }
    }
  }

  private val deferredFailureLog = RateLimitedLog()

  /**
   * Ticks executed since this world was created.
   *
   * For logic that has to say "not before a while from now" and has no other clock: scheduling something a
   * random number of ticks ahead is how a population of NPCs is kept from all reacting to the same event on
   * the same tick. Deliberately a tick count rather than a wall clock, so it advances with the simulation and
   * stays reproducible in a test that drives [tick] by hand.
   */
  var tickCount: Long = 0L
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
