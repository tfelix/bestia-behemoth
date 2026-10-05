package net.bestia.zone.ecs.core

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.util.EntityId

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.reflect.KClass

/**
 * The central ECS facade. Owns entities, component stores, the system scheduler,
 * and the inbound/outbound messaging queues. Everything gameplay-related flows
 * through here.
 *
 * ### Tick pipeline (deterministic, single tick thread)
 * ```
 * tick(dt):
 *   1. run posted work          -> tick-lane messages, leases, skill resolutions
 *   2. drain external commands  -> onCommand handlers
 *   3. run due systems          -> scheduler (parallel waves)
 *   4. apply deferred structural changes emitted by systems
 * ```
 *
 * ### Threading
 * The tick thread owns the world and uses it without a lock. Other threads [post] work for it, or get
 * the world on a lease between two ticks; see [WorldOwnership]. Structural changes requested while
 * systems are iterating are deferred to a safe sync point.
 *
 * ### Outbound sync
 * A component knows whether it needs re-sending (see [Dirtyable]). Mutating it through its own setters
 * marks it dirty, which also enters it into [dirtyLog]; the flush visits only those entries.
 */
class World(
  parallelSystems: Boolean = false,
  idGenerator: EntityIdGenerator,
  systems: Iterable<System>
) : WorldView {
  /** What changed since the last sync; see [Dirtyable] and [SpatiallyIndexed]. */
  val dirtyLog = DirtyLog()

  private val entities = EntityRegistry(idGenerator)
  private val stores = ConcurrentHashMap<KClass<out Component>, ComponentStore<out Component>>()
  private val scheduler = SystemScheduler(parallelSystems)
  private val commands = CommandQueue()
  private val deferred = ConcurrentLinkedQueue<() -> Unit>()

  init {
    scheduler.registerAll(systems)
  }

  private val owner = WorldOwnership()
  private val destroyListeners = CopyOnWriteArrayList<(EntityId) -> Unit>()
  private val componentRemovedListeners = CopyOnWriteArrayList<(EntityId, Component) -> Unit>()

  @Volatile
  private var iterating = false

  /**
   * [WorldView] read scope: runs [block] with the world to itself. Intended for pure reads from off-tick
   * threads; return values/DTOs rather than leaking components out.
   */
  override fun <T> read(block: World.() -> T): T = owner.guarded { this.block() }

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

  override val entityCount: Int get() = entities.count
  val systemCount: Int get() = scheduler.systemCount
  val waveCount: Int get() = scheduler.waveCount

  /** What the systems on the most recent [tick] cost, slowest first. See [SystemScheduler.lastTickBreakdown]. */
  fun lastTickBreakdown(limit: Int = 5): String = scheduler.lastTickBreakdown(limit)

  // ---------------------------------------------------------------- entities
  fun create(): EntityId = owner.guarded { entities.create() }

  fun create(id: EntityId): EntityId = owner.guarded { entities.create(id) }

  override fun isAlive(id: EntityId): Boolean = owner.guarded { entities.isAlive(id) }

  /** Alias for [isAlive] preserving the previous `ZoneServer.hasEntity` naming. */
  override fun hasEntity(id: EntityId): Boolean = isAlive(id)

  /**
   * Atomically creates an entity and runs [configure] on it (typically a batch of [add]s) while
   * with the world to itself, then returns the new id. Replaces `ZoneServer.addEntityWithWriteLock`.
   */
  override fun createEntity(configure: World.(EntityId) -> Unit): EntityId = owner.guarded {
    val id = entities.create()
    this.configure(id)
    id
  }

  override fun createEntity(id: EntityId, configure: World.(EntityId) -> Unit): EntityId = owner.guarded {
    entities.create(id)
    this.configure(id)
    id
  }

  /**
   * Runs [block] against [id] with the world to itself, or returns null if the entity is not
   * alive. Replaces `ZoneServer.withEntityWriteLock` / `withEntityReadLock` (a single tick thread
   * makes read/write locks unnecessary).
   */
  override fun <T> modify(id: EntityId, block: World.(EntityId) -> T): T? = owner.guarded {
    if (!entities.isAlive(id)) null else this.block(id)
  }

  /** Like [modify] but throws [EntityNotAliveException] if [id] is not alive. */
  override fun <T> modifyOrThrow(id: EntityId, block: World.(EntityId) -> T): T =
    modify(id, block) ?: throw EntityNotAliveException(id)

  fun destroy(id: EntityId) = owner.guarded {
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
      @Suppress("UNCHECKED_CAST")
      (store as ComponentStore<Component>).remove(id)
    }
  }

  // -------------------------------------------------------------- components
  @Suppress("UNCHECKED_CAST")
  fun <T : Component> store(type: KClass<T>): ComponentStore<T> =
    stores.computeIfAbsent(type) { ComponentStore(type, dirtyLog = dirtyLog) } as ComponentStore<T>

  /** Enables object pooling (see [ComponentType]) for a component type. */
  fun <T : Component> registerPooled(componentType: ComponentType<T>) {
    stores[componentType.type] =
      ComponentStore(componentType.type, componentType.factory, componentType.reset, dirtyLog = dirtyLog)
  }

  /**
   * Adds a component to [id]. Deferred if called mid-tick. A freshly created component starts
   * dirty (see [Dirtyable]), so adding one already queues it for sync.
   */
  fun <T : Component> add(id: EntityId, component: T): T = owner.guarded {
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

  fun <T : Component> get(id: EntityId, type: KClass<T>): T? = owner.guarded { store(type).get(id) }

  override fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean = owner.guarded { store(type).has(id) }

  fun <T : Component> remove(id: EntityId, type: KClass<T>): T? = owner.guarded {
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

  inline fun <reified T : Component> updateOrThrow(id: EntityId, block: (T) -> Unit) {
    if (!isAlive(id)) {
      return
    }

    val component = get(id, T::class)
      ?: throw ComponentNotFoundException(id, T::class)

    block(component)
  }

  inline fun <reified T : Component> updateOrIgnore(id: EntityId, block: (T) -> Unit) {
    if (!isAlive(id)) {
      return
    }

    val component = get(id, T::class)
      ?: return

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
    return Query(byType, isolateFailures = iterating)
  }

  /** Visits every `(entity, component)` pair currently stored for [type]. */
  fun <T : Component> each(type: KClass<T>, action: (EntityId, T) -> Unit) = owner.guarded {
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
  fun tick(deltaTime: Float) = owner.guarded {
    owner.runQueued()        // posted work: tick-lane messages, leases, skill resolutions
    tickCount++
    commands.drain(this)     // external intent -> handlers
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
