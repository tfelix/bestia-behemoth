package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.ecs.core.Dirtyable
import net.bestia.zone.ecs.core.Removable
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.ecs.core.dirtyableComponentTypes
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.ecs.visibility.EntitySnapshotBuilder
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator

/**
 * Owns the running ecs [World]: it drives the single-threaded tick loop and, after every tick,
 * flushes ECS state changes to the outside world. This replaces the old `ZoneServer` tick loop plus
 * `DirtyComponentUpdateSystem`:
 *
 *  - **component sync**: for each dirty syncable component (each component tracks its own dirty
 *    state via [Dirtyable]) it builds the matching
 *    [net.bestia.zone.message.EntitySMSG] and routes it to whatever [SyncTargets] the
 *    component resolves (all players in range, or a specific set of accounts), and it keeps the
 *    area-of-interest services up to date from changed positions.
 *  - **domain events**: it drains the world outbox ([ZoneEvent]s emitted by systems, e.g. death)
 *    and performs their side effects (loot spawn, vanish broadcast).
 *
 * Network sends and loot spawns are offloaded to a small worker pool so the tick thread never blocks
 * on them, mirroring the previous `queueExternalJob` behaviour.
 */
@Service
class ZoneEngine(
  private val world: World,
  private val config: ZoneConfig,
  private val entityAOIService: EntityAOIService,
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageProcessor: OutMessageProcessor,
  private val asyncJobExecutor: AsyncJobExecutor,
  private val entityVisibility: EntityVisibility,
  private val snapshotBuilder: EntitySnapshotBuilder,
) {

  private data class RemovedComponentRecord(
    val entityId: EntityId,
    val msg: EntitySMSG,
    val targets: SyncTargets,
  )

  /**
   * Position leads deliberately: the client reconciles an arriving path against where it believes the entity
   * is, so it has to be told where the entity *is* before it is told where the entity is *going*. Everything
   * behind it keeps the scan's own order, which is stable - see [dirtyableComponentTypes].
   */
  private val syncableComponentTypes = dirtyableComponentTypes
    .sortedBy { if (it == Position::class) 0 else 1 }

  private val tickExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "zone-tick") }
  private val removedComponentOutbox = ConcurrentLinkedQueue<RemovedComponentRecord>()

  @Volatile
  private var running = false
  private var lastTickTime = System.currentTimeMillis()

  init {
    // Clean the area-of-interest services when an entity leaves the world, and broadcast a vanish
    // to whoever the entity was ever synced to (see notifyVanishOnDestroy).
    world.onDestroy { entityId ->
      entityAOIService.removeEntityPosition(entityId)

      // Keyed by account rather than by entity - and EntityId is a typealias for Long, so nothing
      // catches the difference but the index.
      world.get(entityId, Account::class)?.accountId?.let { playerAOIService.removeEntityPosition(it) }
      // Before forgetting it: notifyVanishOnDestroy asks who was watching, and that answer lives in the
      // index this drops.
      notifyVanishOnDestroy(entityId)
      entityVisibility.forgot(entityId)
    }

    // Turn removals of opted-in components into client notifications. Fires only for explicit
    // single-component removals (not whole-entity destroy), resolving the message and the sync
    // targets while the world lock is still held, the entity's other components are still readable
    // and the owner is still reachable. The removal is the component's own message type re-sent with
    // removed = true, not a separate generic notification - see Removable.
    world.onComponentRemoved { entityId, component ->
      if (component is Removable) {
        removedComponentOutbox.add(
          RemovedComponentRecord(
            entityId,
            component.toRemovedMessage(world, entityId),
            component.syncTargets(world, entityId)
          )
        )
      }
    }
  }

  fun start() {
    if (running) return
    running = true
    lastTickTime = System.currentTimeMillis()

    tickExecutor.submit {
      LOG.info { "Zone ECS engine started @ ${config.tickRate}Hz" }
      while (running) {
        val now = System.currentTimeMillis()
        val deltaTime = (now - lastTickTime) / 1000f
        lastTickTime = now

        try {
          tickOnce(deltaTime)
        } catch (e: Exception) {
          LOG.error(e) { "Error in zone tick: ${e.message}" }
        }

        val budget = 1000L / config.tickRate
        val elapsed = System.currentTimeMillis() - now

        if (elapsed > budget) reportSlowTick(elapsed, budget)

        val sleep = budget - elapsed
        if (sleep > 0) Thread.sleep(sleep)
      }
    }
  }

  /**
   * Says out loud that a tick did not fit in its budget, and what it spent.
   *
   * **Every streaming budget in the zone is per tick**, so a tick that runs at two and a half times its
   * period quietly divides the whole streaming layer's throughput by two and a half. That is not visible from
   * anything else: `chunk-stream`'s numbers all read "per tick", the client sees terrain arriving slowly, and
   * the only symptom is a loading screen that hits its own failsafe with a half-built world behind it. There
   * was no line of log anywhere that said the tick was late.
   *
   * Rate limited to one line a second, with the suppressed count carried on it: a login into dense ground
   * overruns for tens of consecutive ticks, and one warning per tick would bury the breakdown it is for while
   * a single warning would understate a sustained problem as a blip.
   */
  private fun reportSlowTick(elapsedMs: Long, budgetMs: Long) {
    slowTicks++

    val now = System.currentTimeMillis()
    if (now - lastSlowTickReport < SLOW_TICK_REPORT_INTERVAL_MS) return

    val suppressed = slowTicks - 1
    lastSlowTickReport = now
    slowTicks = 0

    LOG.warn {
      "Zone tick took $elapsedMs ms against a $budgetMs ms budget " +
          "(systems $lastWorldTickMs ms, component sync $lastSyncMs ms): ${world.lastTickBreakdown()}" +
          if (suppressed > 0) "; $suppressed more late ticks since the last of these" else ""
    }
  }

  private var lastSlowTickReport = 0L
  private var slowTicks = 0

  /** The tick's two halves, split so the breakdown cannot be misread as the whole cost. */
  private var lastWorldTickMs = 0L
  private var lastSyncMs = 0L

  @PreDestroy
  fun stop() {
    running = false
    tickExecutor.shutdown()
    try {
      if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) tickExecutor.shutdownNow()
    } catch (_: InterruptedException) {
      tickExecutor.shutdownNow()
    }
  }

  /** Runs [action] on the shared [AsyncJobExecutor] pool (network sends, loot spawn, ...). */
  fun queueExternalJob(action: () -> Unit) {
    asyncJobExecutor.submit(action)
  }

  /**
   * Ticks the world once and flushes; exposed for manual/in-process driving (e.g. tests) without
   * the background loop.
   */
  fun tickOnce(deltaTime: Float) {
    val started = System.nanoTime()
    world.tick(deltaTime)

    val ticked = System.nanoTime()
    syncDirtyComponents()

    lastWorldTickMs = (ticked - started) / 1_000_000
    lastSyncMs = (System.nanoTime() - ticked) / 1_000_000
  }

  private fun syncDirtyComponents() {
    val perEntity = LinkedHashMap<EntityId, MutableList<Dirtyable>>()

    world.locked {
      val positionChanged = HashSet<EntityId>()

      for (syncableComponentType in syncableComponentTypes) {
        world.each(syncableComponentType) { id, comp ->
          val dirtyable = comp as Dirtyable

          // Re-indexing is driven by Position.moved, not by the sync flag, because the two are no
          // longer the same question: a walking entity publishes one step in
          // MoveSystem.POSITION_RESYNC_STEPS but has to be indexed on every one of them. See
          // Position.moved for what reads the index.
          if (comp is Position && comp.moved) {
            positionChanged.add(id)
            comp.clearMoved()
          }

          if (!dirtyable.isDirty()) return@each
          perEntity.getOrPut(id) { mutableListOf() }.add(dirtyable)
          dirtyable.clearDirty()
        }
      }

      // Keep the area-of-interest services in sync with any moved entity.
      // TODO i dont see the advantage to do this here vs doing this from the inside from the movement system
      //   where we could just update this AOI service (ideally with a queued job)
      for (id in positionChanged) {
        val pos = world.get(id, Position::class)?.toVec3L() ?: continue
        // A promoted prop (world/prop/PropPromotionService) has just gained a real, dirty Position, and the
        // bare default below would silently re-home it from AoiLayer.STATIC to DYNAMIC - PerceptionSystem
        // queries DYNAMIC_ONLY, so a promoted prop must stay STATIC even while it can move through combat's
        // HP tracking.
        val layer = if (world.has(id, WorldObjectIdentity::class)) AoiLayer.STATIC else AoiLayer.DYNAMIC
        entityAOIService.setEntityPosition(id, pos, layer)

        // StaticSync rather than the layer above: the layer buckets the spatial index, this asks the one
        // question visibility cares about - whether the entity already reaches clients on the static batch.
        if (!world.has(id, StaticSync::class)) entityVisibility.moved(id, pos)

        if (world.has(id, ActivePlayer::class)) {
          val accountId = world.get(id, Account::class)?.accountId
          if (accountId != null) playerAOIService.setEntityPosition(accountId, pos)
        }
      }
    }

    // Build the outbound component update messages outside the world lock: resolving sync
    // targets (e.g. party membership) may hit the database and must not block the tick thread.
    for ((entityId, comps) in perEntity) {
      val pos = world.get(entityId, Position::class)?.toVec3L() ?: continue

      val broadcastMsgs = mutableListOf<SMSG>()
      val byAccountMsgs = LinkedHashMap<Long, MutableList<SMSG>>()

      for (c in comps) {
        val msg = c.toEntityMessage(entityId)

        when (val target = c.syncTargets(world, entityId)) {
          is SyncTargets.PublicInRange -> broadcastMsgs.add(msg)
          is SyncTargets.OwnerOnly -> {
            val ownerAccountId = world.get(entityId, Account::class)
              ?.accountId
              ?: continue
            byAccountMsgs.getOrPut(ownerAccountId) { mutableListOf() }.add(msg)
          }

          is SyncTargets.Accounts -> target.accountIds.forEach { accountId ->
            byAccountMsgs.getOrPut(accountId) { ArrayList() }.add(msg)
          }
        }
      }

      if (broadcastMsgs.isNotEmpty()) {
        publicAudienceOf(entityId).forEach { accountId ->
          asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, broadcastMsgs) }
        }
      }
      byAccountMsgs.forEach { (accountId, msgs) ->
        asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msgs) }
      }
    }

    flushRemovedComponents()
    flushVisibilityChanges()
  }

  /**
   * Who is told about a [SyncTargets.PublicInRange] change to [entityId].
   *
   * The accounts holding the chunk it stands in, plus - always - the account that owns it. A player has to
   * hear about its own entity whatever terrain it happens to be holding: the chunk it is walking into may not
   * have been requested and served yet, and losing sight of yourself for those ticks is never the right
   * answer.
   */
  private fun publicAudienceOf(entityId: EntityId): Set<AccountId> {
    val observers = entityVisibility.observersOf(entityId)

    if (!world.has(entityId, ActivePlayer::class)) return observers

    val owner = world.get(entityId, Account::class)?.accountId ?: return observers

    return if (owner in observers) observers else observers + owner
  }

  /**
   * Sends a full snapshot for every entity that has just come into an account's view.
   *
   * Built here rather than where the change was noticed because this is the one place that is on the tick
   * thread, after every system, and outside the world lock - and a snapshot is a read of up to
   * twenty-five components against sync targets that may look at other entities.
   *
   * No budget of its own: arrivals are driven by chunks going out, which `ChunkStreamSystem` already meters
   * at `chunksPerTickPerPlayer`, so a login spreads over the same second or two the terrain does.
   */
  private fun flushVisibilityChanges() {
    for (delivery in entityVisibility.drain()) {
      val msgs = delivery.appeared.flatMap { entityId ->
        snapshotBuilder.build(world, entityId, delivery.accountId)
      }

      val withVanishes = msgs + delivery.vanished.map {
        VanishEntitySMSG(it, VanishEntitySMSG.VanishKind.OUT_OF_SIGHT)
      }

      if (withVanishes.isEmpty()) continue

      asyncJobExecutor.submit(key = delivery.accountId) {
        outMessageProcessor.sendToPlayer(delivery.accountId, withVanishes)
      }
    }
  }

  /**
   * Drains component removals accumulated this tick and notifies the captured targets. The entity is
   * still alive here (only a single component was removed), so owner resolution for [SyncTargets]
   * that need it works exactly as in the dirty flush.
   */
  private fun flushRemovedComponents() {
    // TODO isnt there a nicer pattern in kotlin? maybe foreach() ?
    while (true) {
      val record = removedComponentOutbox.poll() ?: break
      val msg = record.msg

      when (val targets = record.targets) {
        is SyncTargets.PublicInRange -> publicAudienceOf(record.entityId).forEach { accountId ->
          asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msg) }
        }

        is SyncTargets.OwnerOnly -> {
          val owner = world.get(record.entityId, Account::class)?.accountId ?: continue
          asyncJobExecutor.submit(key = owner) { outMessageProcessor.sendToPlayer(owner, msg) }
        }

        is SyncTargets.Accounts -> targets.accountIds.forEach { accountId ->
          asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msg) }
        }
      }
    }
  }

  /**
   * An entity that was never synced to any client (no [Dirtyable] component) never told a client it
   * existed either, so it needs no vanish. One that was gets a [VanishEntitySMSG] broadcast to the
   * superset of every synced component's [SyncTargets] - called from [World.onDestroy] while the
   * entity's components are still readable (see the ordering note on [World.destroyNow]).
   */
  private fun notifyVanishOnDestroy(entityId: EntityId) {
    val syncedComponents = syncableComponentTypes.mapNotNull { type -> world.get(entityId, type) as? Dirtyable }
    if (syncedComponents.isEmpty()) return

    val targets = mergeSyncTargets(entityId, syncedComponents.map { it.syncTargets(world, entityId) }) ?: return
    val kind = if (world.has(entityId, Dead::class)) VanishEntitySMSG.VanishKind.DEATH else VanishEntitySMSG.VanishKind.GONE
    val msg = VanishEntitySMSG(entityId, kind)

    when (targets) {
      is SyncTargets.PublicInRange -> publicAudienceOf(entityId).forEach { accountId ->
        asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msg) }
      }

      is SyncTargets.Accounts -> targets.accountIds.forEach { accountId ->
        asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msg) }
      }

      is SyncTargets.OwnerOnly -> Unit // never produced by mergeSyncTargets, kept for exhaustiveness
    }
  }

  /**
   * Collapses several components' [SyncTargets] into one: [SyncTargets.PublicInRange] subsumes
   * everything else, so it wins outright; otherwise every [SyncTargets.OwnerOnly] (resolved via the
   * entity's [Account]) and [SyncTargets.Accounts] are unioned into a single [SyncTargets.Accounts].
   * Returns null if nothing resolves to an actual target (e.g. `OwnerOnly` with no `Account`).
   */
  private fun mergeSyncTargets(entityId: EntityId, targets: List<SyncTargets>): SyncTargets? {
    if (targets.any { it is SyncTargets.PublicInRange }) return SyncTargets.PublicInRange

    val accountIds = mutableSetOf<Long>()
    for (target in targets) {
      when (target) {
        is SyncTargets.OwnerOnly -> world.get(entityId, Account::class)?.accountId?.let { accountIds.add(it) }
        is SyncTargets.Accounts -> accountIds.addAll(target.accountIds)
        is SyncTargets.PublicInRange -> Unit
      }
    }

    return if (accountIds.isEmpty()) null else SyncTargets.Accounts(accountIds)
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** Shortest gap between two slow-tick warnings. See [reportSlowTick]. */
    private const val SLOW_TICK_REPORT_INTERVAL_MS = 1_000L
  }
}
