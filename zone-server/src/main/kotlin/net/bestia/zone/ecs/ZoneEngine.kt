package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.battle.damage.Dead
import net.bestia.zone.ecs.core.AsyncJobExecutor
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.entity.VanishEntitySMSG
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
) {

  private data class RemovedComponentRecord(
    val entityId: EntityId,
    val type: RemovableComponentType,
    val targets: SyncTargets,
  )

  private val syncableComponentTypes = scanDirtyableComponentTypes()

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
      playerAOIService.removeEntityPosition(entityId)
      notifyVanishOnDestroy(entityId)
    }

    // Turn removals of opted-in components into client notifications. Fires only for explicit
    // single-component removals (not whole-entity destroy), resolving the sync targets while the
    // world lock is still held and the owner is still reachable.
    world.onComponentRemoved { entityId, component ->
      if (component is RemovalNotifiable && component is Dirtyable) {
        removedComponentOutbox.add(
          RemovedComponentRecord(
            entityId,
            component.removableComponentType,
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

        val sleep = (1000L / config.tickRate) - (System.currentTimeMillis() - now)
        if (sleep > 0) Thread.sleep(sleep)
      }
    }
  }

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
    world.tick(deltaTime)
    syncDirtyComponents()
  }

  private fun syncDirtyComponents() {
    val perEntity = LinkedHashMap<EntityId, MutableList<Dirtyable>>()

    world.locked {
      val positionChanged = HashSet<EntityId>()

      for (syncableComponentType in syncableComponentTypes) {
        world.each(syncableComponentType) { id, comp ->
          val dirtyable = comp as Dirtyable
          if (!dirtyable.isDirty()) return@each
          perEntity.getOrPut(id) { mutableListOf() }.add(dirtyable)
          if (syncableComponentType == Position::class) {
            positionChanged.add(id)
          }
          dirtyable.clearDirty()
        }
      }

      // Keep the area-of-interest services in sync with any moved entity.
      // TODO i dont see the advantage to do this here vs doing this from the inside from the movement system
      //   where we could just update this AOI service (ideally with a queued job)
      for (id in positionChanged) {
        val pos = world.get(id, Position::class)?.toVec3L() ?: continue
        entityAOIService.setEntityPosition(id, pos)
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
        asyncJobExecutor.submit(key = entityId) { outMessageProcessor.sendToAllPlayersInRange(pos, broadcastMsgs) }
      }
      byAccountMsgs.forEach { (accountId, msgs) ->
        asyncJobExecutor.submit(key = accountId) { outMessageProcessor.sendToPlayer(accountId, msgs) }
      }
    }

    flushRemovedComponents()
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
      val msg = ComponentRemovedSMSG(record.entityId, record.type)

      when (val targets = record.targets) {
        is SyncTargets.PublicInRange -> {
          val pos = world.get(record.entityId, Position::class)?.toVec3L() ?: continue
          asyncJobExecutor.submit(key = record.entityId) { outMessageProcessor.sendToAllPlayersInRange(pos, msg) }
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
      is SyncTargets.PublicInRange -> {
        val pos = world.get(entityId, Position::class)?.toVec3L() ?: return
        asyncJobExecutor.submit(key = entityId) { outMessageProcessor.sendToAllPlayersInRange(pos, msg) }
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
  }
}
