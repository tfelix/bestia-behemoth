package net.bestia.zone.ecs

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.account.Account
import net.bestia.zone.ecs.account.ActivePlayer
import net.bestia.zone.ecs.core.Component
import net.bestia.zone.util.EntityId
import net.bestia.zone.ecs.core.World
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.OutMessageProcessor
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.collections.iterator
import kotlin.reflect.KClass

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
) {

  private val syncableComponentTypes: List<KClass<out Component>> by lazy {
    scanDirtyableComponentTypes()
  }

  private val tickExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "zone-tick") }
  private val externalExecutor = Executors.newFixedThreadPool(2) { r -> Thread(r, "zone-external") }

  /**
   * Removals of [RemovalNotifiable] components accumulated during a tick, flushed as
   * [ComponentRemovedSMSG]s in [syncDirtyComponents]. The [SyncTargets] are captured at removal time
   * (while the component instance and its owner are still available) so the flush needs only the id.
   */
  private val removedComponentOutbox = ConcurrentLinkedQueue<RemovedComponentRecord>()

  @Volatile
  private var running = false
  private var lastTickTime = System.currentTimeMillis()

  init {
    // Clean the area-of-interest services when an entity leaves the world.
    world.onDestroy { entityId ->
      entityAOIService.removeEntityPosition(entityId)
      playerAOIService.removeEntityPosition(entityId)
    }

    // Turn removals of opted-in components into client notifications. Fires only for explicit
    // single-component removals (not whole-entity destroy), resolving the sync targets while the
    // world lock is still held and the owner is still reachable.
    world.onComponentRemoved { entityId, component ->
      if (component is RemovalNotifiable && component is Dirtyable) {
        removedComponentOutbox.add(
          RemovedComponentRecord(entityId, component.removableComponentType, component.syncTargets(world, entityId))
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
    externalExecutor.shutdown()
    try {
      if (!tickExecutor.awaitTermination(5, TimeUnit.SECONDS)) tickExecutor.shutdownNow()
      if (!externalExecutor.awaitTermination(5, TimeUnit.SECONDS)) externalExecutor.shutdownNow()
    } catch (_: InterruptedException) {
      tickExecutor.shutdownNow()
      externalExecutor.shutdownNow()
    }
  }

  /** Runs [action] on the external worker pool (network sends, loot spawn, ...). */
  fun queueExternalJob(action: () -> Unit) {
    externalExecutor.submit(action)
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
        externalExecutor.submit { outMessageProcessor.sendToAllPlayersInRange(pos, broadcastMsgs) }
      }
      byAccountMsgs.forEach { (accountId, msgs) ->
        externalExecutor.submit { outMessageProcessor.sendToPlayer(accountId, msgs) }
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
    while (true) {
      val record = removedComponentOutbox.poll() ?: break
      val msg = ComponentRemovedSMSG(record.entityId, record.type)

      when (val targets = record.targets) {
        is SyncTargets.PublicInRange -> {
          val pos = world.get(record.entityId, Position::class)?.toVec3L() ?: continue
          externalExecutor.submit { outMessageProcessor.sendToAllPlayersInRange(pos, msg) }
        }
        is SyncTargets.OwnerOnly -> {
          val owner = world.get(record.entityId, Account::class)?.accountId ?: continue
          externalExecutor.submit { outMessageProcessor.sendToPlayer(owner, msg) }
        }
        is SyncTargets.Accounts -> targets.accountIds.forEach { accountId ->
          externalExecutor.submit { outMessageProcessor.sendToPlayer(accountId, msg) }
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}

private data class RemovedComponentRecord(
  val entityId: EntityId,
  val type: RemovableComponentType,
  val targets: SyncTargets,
)
