package net.bestia.zone.ai.message

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.zone.ai.ecs.AiAgent
import net.bestia.zone.ai.ecs.AiAgentFactory
import net.bestia.zone.ai.profile.AiProfileRegistry
import net.bestia.zone.bestia.PlayerBestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.ecs.core.session.ConnectionInfoService
import net.bestia.zone.ecs.movement.Position
import net.bestia.zone.message.InMessageProcessor
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Applies a player's standing order to one of their own bestias: validate it, store it, and rebuild the live
 * agent so the change takes effect without needing a respawn.
 *
 * Ownership is checked against the *session*, not against a client-supplied account id, following the same rule
 * as every other handler that acts on behalf of a player's creature.
 */
@Component
class SetBestiaAiConfigHandler(
  private val playerBestiaRepository: PlayerBestiaRepository,
  private val connectionInfoService: ConnectionInfoService,
  private val aiProfileRegistry: AiProfileRegistry,
  private val aiAgentFactory: AiAgentFactory,
  private val world: WorldView,
  private val outMessageProcessor: OutMessageProcessor,
) : InMessageProcessor.IncomingMessageHandler<SetBestiaAiConfigCMSG> {

  override val handles = SetBestiaAiConfigCMSG::class

  @Transactional
  override fun handle(msg: SetBestiaAiConfigCMSG): Boolean {
    LOG.trace { "RX: $msg" }

    val stance = msg.stance ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.INVALID_STANCE)

    if (!ownsBestia(msg.playerId, msg.playerBestiaId)) {
      LOG.warn { "Account ${msg.playerId} tried to configure bestia ${msg.playerBestiaId} it does not own" }
      return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED)
    }

    val playerBestia = playerBestiaRepository.findById(msg.playerBestiaId).orElse(null)
      ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED)

    val config = msg.toConfig(stance)
    playerBestia.aiConfig = config
    playerBestiaRepository.save(playerBestia)

    reapplyToLiveEntity(msg.playerId, msg.playerBestiaId, playerBestia.bestia.aiProfile)

    // The stored config, not the requested one — the numbers may have been clamped.
    outMessageProcessor.sendToPlayer(msg.playerId, BestiaAiConfigSMSG(msg.playerBestiaId, config))
    return true
  }

  /**
   * Ownership from the session's own record of which bestias this account has spawned, which is the same source
   * every other handler trusts and needs no extra query.
   */
  private fun ownsBestia(accountId: Long, playerBestiaId: PlayerBestiaId): Boolean {
    val masterId = runCatching { connectionInfoService.getMasterId(accountId) }.getOrNull() ?: return false

    return connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .any { it.playerBestiaId == playerBestiaId }
  }

  /**
   * Swaps in a freshly built agent for the spawned entity, if it is spawned.
   *
   * Rebuilding rather than mutating is deliberate: a stance changes which *goals* the agent has, and those are
   * resolved once when the agent is constructed. Rebuilding keeps one construction path for both spawn and
   * reconfiguration, so there is no second place where a stance could be interpreted differently.
   *
   * Its home is taken from where the creature currently stands, matching what the spawner does — told to hold or
   * patrol, it should do so here, not back where it was first summoned.
   */
  private fun reapplyToLiveEntity(accountId: Long, playerBestiaId: PlayerBestiaId, profileId: String?) {
    if (profileId == null) return
    val profile = aiProfileRegistry.get(profileId) ?: return

    val masterId = runCatching { connectionInfoService.getMasterId(accountId) }.getOrNull() ?: return
    val entityId = connectionInfoService.getOwnedEntitiesByMaster(accountId, masterId)
      .firstOrNull { it.playerBestiaId == playerBestiaId }
      ?.entityId
      ?: return

    val stored = playerBestiaRepository.findById(playerBestiaId).orElse(null)?.aiConfig ?: return

    world.modify(entityId) { id ->
      val home = get(id, Position::class)?.toVec3L() ?: return@modify
      add(id, aiAgentFactory.create(profile, homePosition = home, config = stored))
    }
  }

  private fun deny(playerId: Long, code: AiConfigErrorSMSG.AiConfigErrorCode): Boolean {
    outMessageProcessor.sendToPlayer(playerId, AiConfigErrorSMSG(code))
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
