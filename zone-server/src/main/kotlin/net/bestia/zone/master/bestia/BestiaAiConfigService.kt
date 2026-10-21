package net.bestia.zone.master.bestia

import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiConfig
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.bestia.persistence.Bestia
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.movement.ecs.Position
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.context.ApplicationEventPublisher
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Gives a bestia a new standing order: stores it, and rebuilds the live agent so it takes effect without a respawn.
 * Callers check ownership first, each against its own source - the socket handler against the session, the
 * companion app against the database.
 *
 * The live entity is found by the id on the row, which survives restarts, so this works with nobody logged in.
 * A bestia the zone has not respawned since a restart only gets the row; its spawner reads the order from there.
 */
@Service
class BestiaAiConfigService(
  private val playerBestias: PlayerBestiaRepository,
  private val aiProfileRegistry: AiProfileRegistry,
  private val aiAgentFactory: AiAgentFactory,
  private val world: WorldView,
  private val events: ApplicationEventPublisher,
) {

  /** The config as stored, which has its aggression clamped, or null when the bestia does not exist. */
  @Transactional
  fun apply(playerBestiaId: PlayerBestiaId, requested: AiConfig): AiConfig? {
    val playerBestia = playerBestias.findByIdOrNull(playerBestiaId) ?: return null
    val config = requested.sanitised()

    playerBestia.aiConfig = config
    playerBestias.save(playerBestia)
    playerBestia.entityId?.let { rebuildAgent(it, playerBestia.bestia, config) }

    val master = playerBestia.master
    events.publishEvent(BestiaAiConfigChangedEvent(this, master.account.id, master.id, playerBestiaId, config))
    return config
  }

  /**
   * Rebuilt rather than changed in place: a stance decides which goals the agent has, and those are resolved when
   * it is constructed. Home is where the creature stands now, as at spawn: told to hold or patrol, it does so here.
   */
  private fun rebuildAgent(entityId: Long, species: Bestia, config: AiConfig) {
    val profile = species.aiProfile?.let(aiProfileRegistry::get) ?: return

    world.modify(entityId) { id ->
      val home = get(id, Position::class)?.toVec3L() ?: return@modify
      add(id, aiAgentFactory.create(profile, species.defaultAttack, species.aspd, homePosition = home, config = config))
    }
  }
}
