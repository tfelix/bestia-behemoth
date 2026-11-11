package net.bestia.zone.master.bestia

import net.bestia.zone.bestia.diary.BestiaHappened
import net.bestia.zone.crafting.station.StationWorkers
import net.bestia.zone.account.persistence.OrderPreset
import net.bestia.zone.account.persistence.OrderPresetRepository
import net.bestia.zone.account.persistence.PlayerBestia
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.ai.profile.OrderOverrides
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Gives a bestia new orders: stores them, and rebuilds the live agent so it takes effect without a respawn.
 * Callers check ownership first, each against its own source - the socket handler against the session, the
 * companion app against the database.
 *
 * The live entity is found by the id on the row, which survives restarts, so this works with nobody logged in.
 * A bestia the zone has not respawned since a restart only gets the row; its spawner reads the order from there.
 */
@Service
class BestiaAiConfigService(
  private val playerBestias: PlayerBestiaRepository,
  private val presets: OrderPresetRepository,
  private val places: NamedPlaces,
  private val aiProfileRegistry: AiProfileRegistry,
  private val aiAgentFactory: AiAgentFactory,
  private val world: WorldView,
  private val events: ApplicationEventPublisher,
  private val stationWorkers: StationWorkers,
) {

  /**
   * Gives a bestia [orders] of its own, or links it to the preset [presetId] with the numbers of [orders] as its own.
   * Null when the bestia does not exist, or the preset or a place it names is not its master's.
   */
  @Transactional
  fun apply(playerBestiaId: PlayerBestiaId, orders: AiConfig, presetId: Long? = null): StoredOrders? {
    val playerBestia = playerBestias.findByIdOrNull(playerBestiaId) ?: return null
    if (!places.fitFor(playerBestia.master.id, orders)) return null
    val own = orders.overrides.clamped()

    if (presetId == null) {
      playerBestia.orderPresetId = null
      playerBestia.ownOverrides = OrderOverrides()
      playerBestia.aiConfig = orders.copy(overrides = own)
    } else {
      val preset = presets.findByIdAndMasterId(presetId, playerBestia.master.id) ?: return null
      follow(playerBestia, preset, own)
    }

    return store(playerBestia)
  }

  @Transactional(readOnly = true)
  fun storedOf(playerBestiaId: PlayerBestiaId): StoredOrders? {
    return playerBestias.findByIdOrNull(playerBestiaId)?.let(StoredOrders::of)
  }

  /** Gives every bestia that follows [preset] its orders again, each keeping its own numbers. */
  @Transactional
  fun reapply(preset: OrderPreset) {
    playerBestias.findAllByOrderPresetId(preset.id).forEach { playerBestia ->
      follow(playerBestia, preset, playerBestia.ownOverrides)
      store(playerBestia)
    }
  }

  /** Bestias that followed a deleted preset keep the orders they had, as their own. */
  @Transactional
  fun unlinkAll(presetId: Long) {
    playerBestias.findAllByOrderPresetId(presetId).forEach { playerBestia ->
      playerBestia.orderPresetId = null
      playerBestia.ownOverrides = OrderOverrides()
      store(playerBestia)
    }
  }

  private fun follow(playerBestia: PlayerBestia, preset: OrderPreset, own: OrderOverrides) {
    val presetOrders = preset.orders
    playerBestia.orderPresetId = preset.id
    playerBestia.ownOverrides = own
    playerBestia.aiConfig = presetOrders.copy(overrides = presetOrders.overrides.overlaidWith(own).clamped())
  }

  private fun store(playerBestia: PlayerBestia): StoredOrders {
    playerBestias.save(playerBestia)
    val stored = StoredOrders.of(playerBestia)
    playerBestia.entityId?.let { rebuildAgent(it, playerBestia.bestia, stored.orders) }

    val master = playerBestia.master
    events.publishEvent(BestiaAiConfigChangedEvent(this, master.account.id, master.id, playerBestia.id, stored))
    events.publishEvent(
      BestiaHappened(this, master.id, playerBestia.id, BestiaHappened.Kind.NEW_ORDERS, reason = stored.orders.job.name)
    )
    return stored
  }

  /**
   * Rebuilt rather than changed in place: a job decides which goals the agent has, and those are resolved when
   * it is constructed. Home is where the creature stands now, as at spawn: told to guard or rest, it does so here.
   */
  private fun rebuildAgent(entityId: Long, species: Bestia, config: AiConfig) {
    val profile = species.aiProfile?.let(aiProfileRegistry::get) ?: return

    world.modify(entityId) { id ->
      val home = get(id, Position::class)?.toVec3L() ?: return@modify
      add(id, aiAgentFactory.create(profile, species.defaultAttack, species.aspd, homePosition = home, config = config))
      // A bestia at work keeps working: its home stays the station.
      stationWorkers.resume(this, id)
    }
  }
}
