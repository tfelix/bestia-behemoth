package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PreDestroy
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.sync.Dirtyable
import net.bestia.zone.ecs.core.RateLimitedLog
import net.bestia.zone.sync.Removable
import net.bestia.zone.util.AccountId
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.EcsWorld
import net.bestia.zone.ecs.core.World
import net.bestia.zone.sync.dirtyableComponentTypes
import net.bestia.zone.ecs.core.isFatal
import net.bestia.zone.ecs.prop.StaticSync
import net.bestia.zone.ecs.prop.WorldObjectIdentity
import net.bestia.zone.ecs.visibility.EntityAudience
import net.bestia.zone.ecs.visibility.EntitySnapshotBuilder
import net.bestia.zone.ecs.visibility.EntityVisibility
import net.bestia.zone.entity.VanishEntitySMSG
import net.bestia.zone.message.EntitySMSG
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.TickOutbox
import net.bestia.zone.metrics.TickMetrics
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator
import net.bestia.zone.sync.SyncTargets
import net.bestia.zone.config.WorldRulesConfig

/**
 * Owns the running ecs [World]: it drives the single-threaded tick loop and, after every tick, syncs
 * component changes to clients. For each dirty syncable component (see [Dirtyable]) it builds the matching
 * [net.bestia.zone.message.EntitySMSG] and routes it to whatever [SyncTargets] the component resolves, keeps
 * the area-of-interest services up to date from changed positions, and broadcasts a vanish for each
 * destroyed entity.
 *
 * Everything a tick sends is collected in the [TickOutbox] and leaves as one batch per account when the
 * tick ends; `channel.write` does not block, so the tick never waits on the network.
 */
@Service
class ZoneEngine(
  private val world: EcsWorld,
  private val config: WorldRulesConfig,
  private val entityAOIService: EntityAOIService,
  private val playerAOIService: ActivePlayerAOIService,
  private val outMessageProcessor: OutMessageProcessor,
  private val outbox: TickOutbox,
  private val entityVisibility: EntityVisibility,
  private val entityAudience: EntityAudience,
  private val snapshotBuilder: EntitySnapshotBuilder,
  private val tickMetrics: TickMetrics,
) {

  private data class RemovedComponentRecord(
    val entityId: EntityId,
    val msg: EntitySMSG,
    val targets: SyncTargets,
  )

  private val syncableComponentTypes = dirtyableComponentTypes

  private val tickExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, TICK_THREAD_NAME) }
  private val removedComponentOutbox = ConcurrentLinkedQueue<RemovedComponentRecord>()

  @Volatile
  private var running = false

  private val stepNanos = TimeUnit.SECONDS.toNanos(1) / config.tickRate
  private val stepSeconds = 1f / config.tickRate
  private var clock = FixedStepClock(stepNanos, MAX_CATCH_UP_STEPS, System.nanoTime())

  init {
    // Clean the area-of-interest services when an entity leaves the world, and broadcast a vanish
    // to whoever the entity was ever synced to (see notifyVanishOnDestroy).
    world.onDestroy { entityId ->
      entityAOIService.removeEntityPosition(entityId)

      // Keyed by account rather than by entity - and EntityId is a typealias for Long, so nothing
      // catches the difference but the index. Only the anchor clears it: an account owns several entities,
      // and losing one it is not looking through must not blind it.
      if (world.has(entityId, ActivePlayer::class)) {
        world.get(entityId, Account::class)?.accountId?.let { playerAOIService.removeEntityPosition(it) }
      }
      // Before forgetting it: notifyVanishOnDestroy asks who was watching, and that answer lives in the
      // index this drops.
      notifyVanishOnDestroy(entityId)
      entityVisibility.forgot(entityId)
    }

    // Turn removals of opted-in components into client notifications. Fires only for explicit
    // single-component removals (not whole-entity destroy), resolving the message and the sync
    // targets right away, while the entity's other components are still readable and the owner is
    // still reachable. The removal is the component's own message type re-sent with
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
    clock = FixedStepClock(stepNanos, MAX_CATCH_UP_STEPS, System.nanoTime())

    // execute, not submit: a Future nobody reads would swallow the error that ends the loop.
    tickExecutor.execute {
      try {
        runTickLoop()
      } catch (e: Throwable) {
        LOG.error(e) { "Zone tick loop died, the world no longer advances" }
        throw e
      }
    }
  }

  /** Every step hands the systems the same delta; time lost to a long pause is dropped, see [FixedStepClock]. */
  private fun runTickLoop() {
    LOG.info { "Zone ECS engine started @ ${config.tickRate}Hz" }
    world.bindTickThread()
    try {
      while (running) {
        val droppedBefore = clock.droppedSteps
        val due = clock.dueSteps(System.nanoTime())
        tickMetrics.stepsDropped(clock.droppedSteps - droppedBefore)

        repeat(due) { tickSafely() }
        awaitNextStep()
      }
    } finally {
      world.unbindTickThread()
    }
  }

  private fun tickSafely() {
    val started = System.nanoTime()

    try {
      tickOnce(stepSeconds)
    } catch (e: Throwable) {
      if (e.isFatal()) throw e
      LOG.error(e) { "Error in zone tick: ${e.message}" }
    }

    val elapsed = System.nanoTime() - started
    tickMetrics.recordTotal(elapsed)
    if (elapsed > stepNanos) reportSlowTick(elapsed)
  }

  /** Between steps the tick thread runs posted work (tick-lane messages, leases), each burst one outbox batch. */
  private fun awaitNextStep() {
    while (running && clock.nextStepAt - System.nanoTime() > 0) {
      try {
        outbox.collect { world.runPostedUntil(clock.nextStepAt) }
      } catch (e: Throwable) {
        if (e.isFatal()) throw e
        LOG.error(e) { "Error while running posted work: ${e.message}" }
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
  private fun reportSlowTick(elapsedNanos: Long) {
    slowTicks++

    val now = System.nanoTime()
    val last = lastSlowTickReport
    if (last != null && now - last < SLOW_TICK_REPORT_INTERVAL_NANOS) return

    val suppressed = slowTicks - 1
    val dropped = clock.droppedSteps - droppedStepsReported
    lastSlowTickReport = now
    slowTicks = 0
    droppedStepsReported = clock.droppedSteps

    LOG.warn {
      "Zone tick took ${elapsedNanos / 1_000_000} ms against a ${stepNanos / 1_000_000} ms budget " +
          "(systems ${lastSystemsNanos / 1_000_000} ms, component sync ${lastSyncNanos / 1_000_000} ms): " +
          world.lastTickBreakdown() +
          (if (suppressed > 0) "; $suppressed more late ticks since the last of these" else "") +
          if (dropped > 0) "; $dropped steps dropped to catch up" else ""
    }
  }

  private var lastSlowTickReport: Long? = null
  private var slowTicks = 0
  private var droppedStepsReported = 0L

  /** The tick's two halves, split so the breakdown cannot be misread as the whole cost. */
  private var lastSystemsNanos = 0L
  private var lastSyncNanos = 0L

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

  /**
   * Ticks the world once and flushes; exposed for manual/in-process driving (e.g. tests) without
   * the background loop.
   */
  fun tickOnce(deltaTime: Float) {
    outbox.collect {
      val started = System.nanoTime()
      world.tick(deltaTime)

      val ticked = System.nanoTime()
      syncDirtyComponents()

      lastSystemsNanos = ticked - started
      lastSyncNanos = System.nanoTime() - ticked
      tickMetrics.recordParts(lastSystemsNanos, lastSyncNanos)
    }
  }

  private fun syncDirtyComponents() {
    reindexMoved()

    val perEntity = LinkedHashMap<EntityId, MutableList<Dirtyable>>()
    world.dirtyLog.drainDirtied { id, type ->
      // A stale entry is normal: the component may since have been removed, replaced or already sent.
      val component = world.get(id, type) as? Dirtyable ?: return@drainDirtied
      if (!component.isDirty()) return@drainDirtied

      perEntity.getOrPut(id) { mutableListOf() }.add(component)
      component.clearDirty()
    }

    // Drained before the changes go out: a snapshot already carries every public component of an entity in the
    // order a client needs, so its receivers must not also get the changes, unordered and ahead of it.
    val deliveries = entityVisibility.drain()
    val snapshotReceivers = snapshotReceiversByEntity(deliveries)

    for ((entityId, comps) in perEntity) {
      try {
        sendChanges(entityId, comps, snapshotReceivers[entityId].orEmpty())
      } catch (e: Exception) {
        // One component that cannot describe itself must not cost every other entity its update.
        syncFailureLog.emit { held -> LOG.error(e) { "Could not sync entity $entityId (+$held more)" } }
      }
    }

    flushRemovedComponents()
    flushVisibilityChanges(deliveries)
  }

  private fun snapshotReceiversByEntity(deliveries: List<EntityVisibility.Delivery>): Map<EntityId, Set<AccountId>> {
    val receivers = HashMap<EntityId, MutableSet<AccountId>>()

    for (delivery in deliveries) {
      delivery.appeared.forEach { entityId -> receivers.getOrPut(entityId) { HashSet() }.add(delivery.accountId) }
    }

    return receivers
  }

  /**
   * Keeps the area-of-interest services in step with every moved entity. Driven by [Position.moved] rather than
   * by the sync flag: a walking entity publishes one step in `MoveSystem.POSITION_RESYNC_STEPS` but has to be
   * indexed on every one of them.
   */
  private fun reindexMoved() {
    world.dirtyLog.drainMoved { id ->
      val position = world.get(id, Position::class) ?: return@drainMoved
      if (!position.moved) return@drainMoved

      position.clearMoved()
      val pos = position.toVec3L()

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

  private val syncFailureLog = RateLimitedLog()

  private fun sendChanges(entityId: EntityId, comps: List<Dirtyable>, gettingSnapshot: Set<AccountId>) {
    val broadcastMsgs = mutableListOf<SMSG>()
    val byAccountMsgs = LinkedHashMap<Long, MutableList<SMSG>>()

    // Position leads deliberately: the client reconciles an arriving path against where it believes the entity
    // is, so it has to be told where the entity *is* before it is told where it is *going*. The dirty log is in
    // the order components were first marked, which puts a path set by a handler ahead of the position the
    // tick then moves. Stable, so everything behind Position keeps that order.
    for (c in comps.sortedBy { if (it is Position) 0 else 1 }) {
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
      publicAudienceOf(entityId).filterNot { it in gettingSnapshot }.forEach { accountId ->
        outMessageProcessor.sendToPlayer(accountId, broadcastMsgs)
      }
    }
    byAccountMsgs.forEach { (accountId, msgs) ->
      outMessageProcessor.sendToPlayer(accountId, msgs)
    }
  }

  /** Who is told about a [SyncTargets.PublicInRange] change to [entityId]; see [EntityAudience]. */
  private fun publicAudienceOf(entityId: EntityId): Set<AccountId> {
    return entityAudience.of(world, entityId)
  }

  /**
   * Sends a full snapshot for every entity that has just come into an account's view.
   *
   * Built here rather than where the change was noticed because this is the one place that is on the tick
   * thread after every system - and a snapshot is a read of up to twenty-five components against sync
   * targets that may look at other entities.
   *
   * No budget of its own: arrivals are driven by chunks going out, which `ChunkStreamSystem` already meters
   * at `chunksPerTickPerPlayer`, so a login spreads over the same second or two the terrain does.
   */
  private fun flushVisibilityChanges(deliveries: List<EntityVisibility.Delivery>) {
    // One snapshot per entity per tick, however many accounts it appears to.
    val snapshots = HashMap<EntityId, EntitySnapshotBuilder.Snapshot>()

    for (delivery in deliveries) {
      val msgs = delivery.appeared.flatMap { entityId ->
        snapshots.getOrPut(entityId) { snapshotBuilder.snapshotOf(world, entityId) }.visibleTo(delivery.accountId)
      }

      val withVanishes = msgs + delivery.vanished.map {
        VanishEntitySMSG(it, VanishEntitySMSG.VanishKind.OUT_OF_SIGHT)
      }

      if (withVanishes.isEmpty()) continue

      outMessageProcessor.sendToPlayer(delivery.accountId, withVanishes)
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
          outMessageProcessor.sendToPlayer(accountId, msg)
        }

        is SyncTargets.OwnerOnly -> {
          val owner = world.get(record.entityId, Account::class)?.accountId ?: continue
          outMessageProcessor.sendToPlayer(owner, msg)
        }

        is SyncTargets.Accounts -> targets.accountIds.forEach { accountId ->
          outMessageProcessor.sendToPlayer(accountId, msg)
        }
      }
    }
  }

  /**
   * An entity that was never synced to any client (no [Dirtyable] component) never told a client it
   * existed either, so it needs no vanish. One that was gets a [VanishEntitySMSG] broadcast to the
   * superset of every synced component's [SyncTargets] - called from [EcsWorld.onDestroy] while the
   * entity's components are still readable (see the ordering note on [EcsWorld.destroyNow]).
   */
  private fun notifyVanishOnDestroy(entityId: EntityId) {
    val syncedComponents = syncableComponentTypes.mapNotNull { type -> world.get(entityId, type) as? Dirtyable }
    if (syncedComponents.isEmpty()) return

    val targets = mergeSyncTargets(entityId, syncedComponents.map { it.syncTargets(world, entityId) }) ?: return
    val kind = if (world.has(entityId, Dead::class)) VanishEntitySMSG.VanishKind.DEATH else VanishEntitySMSG.VanishKind.GONE
    val msg = VanishEntitySMSG(entityId, kind)

    when (targets) {
      is SyncTargets.PublicInRange -> publicAudienceOf(entityId).forEach { accountId ->
        outMessageProcessor.sendToPlayer(accountId, msg)
      }

      is SyncTargets.Accounts -> targets.accountIds.forEach { accountId ->
        outMessageProcessor.sendToPlayer(accountId, msg)
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

    const val TICK_THREAD_NAME = "zone-tick"

    /** Shortest gap between two slow-tick warnings. See [reportSlowTick]. */
    private val SLOW_TICK_REPORT_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(1)

    /** Steps a late loop may run back to back before it gives up on the rest of the backlog. */
    private const val MAX_CATCH_UP_STEPS = 3
  }
}
