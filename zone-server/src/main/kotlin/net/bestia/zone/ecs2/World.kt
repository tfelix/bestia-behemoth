package net.bestia.zone.ecs2

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
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
 * Component-change marks and emitted events are left populated for the messaging
 * layer to [drainChanges] / [publishChanges] / [drainOutbox] after each tick.
 *
 * ### Threading
 * Only the tick thread mutates ECS state. Other threads may [send] commands,
 * [drainOutbox], and [drainChanges] (all thread-safe). Structural changes
 * requested while systems are iterating are automatically deferred to a safe
 * sync point.
 */
class World(
  parallelSystems: Boolean = false,
  idGenerator: (() -> EntityId)? = null,
) {
  private val entities = if (idGenerator != null) EntityRegistry(idGenerator) else EntityRegistry()
  private val stores = ConcurrentHashMap<KClass<out Component>, ComponentStore<out Component>>()
  private val scheduler = SystemScheduler(parallelSystems)
  private val commands = CommandQueue()
  private val changes = ChangeTracker()
  private val outbox = Outbox()
  private val deferred = ConcurrentLinkedQueue<() -> Unit>()

  @Volatile
  private var iterating = false

  val entityCount: Int get() = entities.count
  val systemCount: Int get() = scheduler.systemCount
  val waveCount: Int get() = scheduler.waveCount

  // ---------------------------------------------------------------- entities
  fun create(): EntityId = entities.create()

  fun create(id: EntityId): EntityId = entities.create(id)

  fun isAlive(id: EntityId): Boolean = entities.isAlive(id)

  fun destroy(id: EntityId) {
    if (iterating) deferred.add { destroyNow(id) } else destroyNow(id)
  }

  private fun destroyNow(id: EntityId) {
    if (!entities.destroy(id)) return
    for (store in stores.values) {
      @Suppress("UNCHECKED_CAST")
      (store as ComponentStore<Component>).remove(id)
    }
    changes.forget(id)
  }

  // -------------------------------------------------------------- components
  @Suppress("UNCHECKED_CAST")
  fun <T : Component> store(type: KClass<T>): ComponentStore<T> =
    stores.computeIfAbsent(type) { ComponentStore(type) } as ComponentStore<T>

  /** Enables object pooling (see [ComponentType]) for a component type. */
  fun <T : Component> registerPooled(componentType: ComponentType<T>) {
    stores[componentType.type] = ComponentStore(componentType.type, componentType.factory, componentType.reset)
  }

  /** Adds a component to [id], marking it changed. Deferred if called mid-tick. */
  fun <T : Component> add(id: EntityId, component: T): T {
    if (iterating) {
      deferred.add { addNow(id, component) }
    } else {
      addNow(id, component)
    }
    return component
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T : Component> addNow(id: EntityId, component: T) {
    require(entities.isAlive(id)) { "Cannot add component to dead entity $id" }
    store(component::class as KClass<T>).set(id, component)
    changes.mark(component::class, id)
  }

  fun <T : Component> get(id: EntityId, type: KClass<T>): T? = store(type).get(id)

  fun <T : Component> has(id: EntityId, type: KClass<T>): Boolean = store(type).has(id)

  fun <T : Component> remove(id: EntityId, type: KClass<T>): T? {
    if (iterating) {
      deferred.add { removeNow(id, type) }
      return null
    }
    return removeNow(id, type)
  }

  private fun <T : Component> removeNow(id: EntityId, type: KClass<T>): T? {
    val removed = store(type).remove(id)
    if (removed != null) changes.mark(type, id)
    return removed
  }

  /** Flags a component of [id] as changed this tick (for outbound sync). */
  fun markChanged(id: EntityId, type: KClass<out Component>) = changes.mark(type, id)

  // reified conveniences
  inline fun <reified T : Component> get(id: EntityId): T? = get(id, T::class)
  inline fun <reified T : Component> has(id: EntityId): Boolean = has(id, T::class)
  inline fun <reified T : Component> remove(id: EntityId): T? = remove(id, T::class)
  inline fun <reified T : Component> markChanged(id: EntityId) = markChanged(id, T::class)

  // ------------------------------------------------------------------ queries
  fun <A : Component> query(a: KClass<A>) = Query1(store(a))

  fun <A : Component, B : Component> query(a: KClass<A>, b: KClass<B>) =
    Query2(store(a), store(b))

  fun <A : Component, B : Component, C : Component> query(a: KClass<A>, b: KClass<B>, c: KClass<C>) =
    Query3(store(a), store(b), store(c))

  // ------------------------------------------------------------------ systems
  fun addSystem(system: Ecs2System) = scheduler.register(system)

  fun addSystems(systems: Iterable<Ecs2System>) = scheduler.registerAll(systems)

  // ------------------------------------------------------------- messaging in
  /** Enqueue external intent from any thread. Applied at the start of next tick. */
  fun send(command: Command) = commands.enqueue(command)

  fun <T : Command> onCommand(type: KClass<T>, handler: (World, T) -> Unit) = commands.on(type, handler)

  inline fun <reified T : Command> onCommand(noinline handler: (World, T) -> Unit) =
    onCommand(T::class, handler)

  // ------------------------------------------------------------ messaging out
  /** Emit a discrete domain event for external consumption. */
  fun emit(event: Any) = outbox.emit(event)

  fun onChanged(type: KClass<out Component>, handler: (EntityId) -> Unit) = changes.on(type, handler)

  inline fun <reified T : Component> onChanged(noinline handler: (EntityId) -> Unit) =
    onChanged(T::class, handler)

  /** Pull model: consume + clear the entities whose [type] component changed. */
  fun drainChanges(type: KClass<out Component>, action: (EntityId) -> Unit) = changes.drain(type, action)

  inline fun <reified T : Component> drainChanges(noinline action: (EntityId) -> Unit) =
    drainChanges(T::class, action)

  /** Push model: fan all pending component changes out to registered observers. */
  fun publishChanges() = changes.publish()

  /** Consume all emitted domain events. */
  fun drainOutbox(action: (Any) -> Unit) = outbox.drain(action)

  // --------------------------------------------------- deferred structural ops
  /** Run [block] now, or defer it to the next safe sync point if mid-tick. */
  fun defer(block: () -> Unit) {
    if (iterating) deferred.add(block) else block()
  }

  private fun applyDeferred() {
    while (true) {
      val job = deferred.poll() ?: break
      job()
    }
  }

  // -------------------------------------------------------------- tick pipeline
  fun tick(deltaTime: Float) {
    applyDeferred()          // 1: structural changes queued last tick
    commands.drain(this)     // 2: external intent -> handlers
    iterating = true
    try {
      scheduler.tick(this, deltaTime) // 3: due systems (parallel waves)
    } finally {
      iterating = false
    }
    applyDeferred()          // 4: structural changes emitted by systems
  }
}
