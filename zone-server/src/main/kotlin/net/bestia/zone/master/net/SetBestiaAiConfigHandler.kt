package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.util.PlayerBestiaId
import org.springframework.stereotype.Component
import net.bestia.zone.master.bestia.AiConfigErrorSMSG
import net.bestia.zone.master.bestia.BestiaAiConfigService
import net.bestia.zone.master.bestia.BestiaAiConfigSMSG

/**
 * Applies a player's standing order to one of their own bestias, through [BestiaAiConfigService].
 *
 * Ownership is checked against the *session*, not against a client-supplied account id, following the same rule
 * as every other handler that acts on behalf of a player's creature.
 */
@Component
class SetBestiaAiConfigHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val aiConfigs: BestiaAiConfigService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<SetBestiaAiConfigCMSG> {

  override val wire = decoder(MessageCase.SET_BESTIA_AI_CONFIG) { accountId, envelope ->
    SetBestiaAiConfigCMSG.fromBnet(accountId, envelope.setBestiaAiConfig)
  }

  override fun handle(msg: SetBestiaAiConfigCMSG): Boolean {
    val stance = msg.stance ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.INVALID_STANCE)

    if (!ownsBestia(msg.playerId, msg.playerBestiaId)) {
      LOG.warn { "Account ${msg.playerId} tried to configure bestia ${msg.playerBestiaId} it does not own" }
      return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED)
    }

    // The stored config, not the requested one — the numbers may have been clamped.
    val stored = aiConfigs.apply(msg.playerBestiaId, msg.toConfig(stance))
      ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED)

    outMessageProcessor.sendToPlayer(msg.playerId, BestiaAiConfigSMSG(msg.playerBestiaId, stored))
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

  private fun deny(playerId: Long, code: AiConfigErrorSMSG.AiConfigErrorCode): Boolean {
    outMessageProcessor.sendToPlayer(playerId, AiConfigErrorSMSG(code))
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
