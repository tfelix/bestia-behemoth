package net.bestia.zone.engine

import io.github.oshai.kotlinlogging.KotlinLogging
import jakarta.annotation.PostConstruct
import net.bestia.zone.ecs.Dirtyable
import net.bestia.zone.ecs.ActivePlayerAOIService
import net.bestia.zone.ecs.EntityAOIService
import net.bestia.zone.ecs.ZoneConfig
import net.bestia.zone.ecs.battle.Health
import net.bestia.zone.ecs.battle.Mana
import net.bestia.zone.ecs.item.Inventory
import net.bestia.zone.ecs.item.Loot
import net.bestia.zone.ecs.movement.Path
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.ecs.movement.Speed
import net.bestia.zone.ecs.player.Account
import net.bestia.zone.ecs.player.ActivePlayer
import net.bestia.zone.ecs.status.Exp
import net.bestia.zone.ecs.status.Level
import net.bestia.zone.ecs.visual.BestiaVisual
import net.bestia.zone.ecs2.Component
import net.bestia.zone.ecs2.EntityId
import net.bestia.zone.ecs2.World
import net.bestia.zone.item.LootEntityFactory
import net.bestia.zone.message.SMSG
import net.bestia.zone.message.entity.VanishEntitySMSG
import net.bestia.zone.message.processor.OutMessageProcessor
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Service
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

/**
 * Owns the running ecs2 [World]: it drives the single-threaded tick loop and, after every tick,
 * flushes ECS state changes to the outside world. This replaces the old `ZoneServer` tick loop plus
 * `DirtyComponentUpdateSystem`:
 *
 *  - **component sync**: for each changed (marked) syncable component it builds the matching
 *    [net.bestia.zone.message.entity.EntitySMSG] and routes it (public -> players in range,
 *    only-owner -> the owning account), and it keeps the area-of-interest services up to date from
 *    changed positions.
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
  @Lazy private val lootEntityFactory: LootEntityFactory,
) {
  private val tickExecutor = Executors.newSingleThreadExecutor { r -> Thread(r, "zone-tick") }
  private val externalExecutor = Executors.newFixedThreadPool(2) { r -> Thread(r, "zone-external") }

  @Volatile
  private var running = false
  private var lastTickTime = System.currentTimeMillis()

  @PostConstruct
  fun registerHooks() {
    // Clean the area-of-interest services when an entity leaves the world.
    world.onDestroy { id ->
      entityAOIService.removeEntityPosition(id)
      playerAOIService.removeEntityPosition(id)
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
          world.tick(deltaTime)
          flush()
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
    flush()
  }

  private fun flush() {
    val sends = ArrayList<() -> Unit>()

    world.locked {
      val perEntity = LinkedHashMap<EntityId, MutableList<Dirtyable>>()
      val positionChanged = HashSet<EntityId>()

      for (type in SYNC_TYPES) {
        world.drainChanges(type) { id ->
          val comp = world.get(id, type) as? Dirtyable ?: return@drainChanges
          perEntity.getOrPut(id) { ArrayList() }.add(comp)
          if (type == Position::class) positionChanged.add(id)
        }
      }

      // Keep the area-of-interest services in sync with any moved entity.
      for (id in positionChanged) {
        val pos = world.get(id, Position::class)?.toVec3L() ?: continue
        entityAOIService.setEntityPosition(id, pos)
        if (world.has(id, ActivePlayer::class)) {
          val accountId = world.get(id, Account::class)?.accountId
          if (accountId != null) playerAOIService.setEntityPosition(accountId, pos)
        }
      }

      // Build the outbound component update messages (old system required a Position to sync).
      for ((id, comps) in perEntity) {
        val pos = world.get(id, Position::class)?.toVec3L() ?: continue
        val hasAccount = world.has(id, Account::class)
        val ownerAccountId = world.get(id, Account::class)?.accountId

        val publicMsgs = ArrayList<SMSG>()
        val privateMsgs = ArrayList<SMSG>()

        for (c in comps) {
          val msg = c.toEntityMessage(id)
          when (c.broadcastType()) {
            Dirtyable.BroadcastType.PUBLIC -> publicMsgs.add(msg)
            Dirtyable.BroadcastType.ONLY_OWNER -> {
              // Health is public for non-account (mob) entities so nearby players see mob HP.
              if (c is Health && !hasAccount) publicMsgs.add(msg) else privateMsgs.add(msg)
            }
          }
        }

        if (publicMsgs.isNotEmpty()) {
          sends.add { outMessageProcessor.sendToAllPlayersInRange(pos, publicMsgs) }
        }
        if (privateMsgs.isNotEmpty() && ownerAccountId != null) {
          sends.add { outMessageProcessor.sendToPlayer(ownerAccountId, privateMsgs) }
        }
      }
    }

    // Discrete domain events emitted by systems (death, ...).
    world.drainOutbox { event -> handleEvent(event, sends) }

    sends.forEach { externalExecutor.submit(it) }
  }

  private fun handleEvent(event: Any, sends: MutableList<() -> Unit>) {
    when (event) {
      is EntityDiedEvent -> {
        sends.add {
          outMessageProcessor.sendToAllPlayersInRange(
            event.position,
            VanishEntitySMSG(entityId = event.entityId, kind = VanishEntitySMSG.VanishKind.DEATH)
          )
        }
        if (event.lootBestiaId != null) {
          sends.add { lootEntityFactory.createLootEntities(event.lootBestiaId, event.position) }
        }
      }
    }
  }

  companion object {
    private val LOG = KotlinLogging.logger { }

    /** All syncable ([Dirtyable]) component types, drained each tick to produce client updates. */
    private val SYNC_TYPES: List<KClass<out Component>> = listOf(
      Position::class,
      Speed::class,
      Path::class,
      Health::class,
      Mana::class,
      Inventory::class,
      Loot::class,
      Exp::class,
      Level::class,
      BestiaVisual::class,
    )
  }
}
