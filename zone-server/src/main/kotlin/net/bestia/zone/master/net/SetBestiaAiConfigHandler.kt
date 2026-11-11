package net.bestia.zone.master.net

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component
import net.bestia.zone.master.bestia.AiConfigErrorSMSG
import net.bestia.zone.master.bestia.BestiaAiConfigService

/**
 * Applies a player's orders to one of their own bestias, through [BestiaAiConfigService].
 *
 * Ownership is checked against the *session*, not against a client-supplied account id, following the same rule
 * as every other handler that acts on behalf of a player's creature. The new orders reach the client through
 * [OrdersToGame], as a change from the companion app does.
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
    val orders = msg.orders ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.INVALID_ORDERS)

    if (connectionInfoService.ownedBestiaOf(msg.playerId, msg.playerBestiaId) == null) {
      LOG.warn { "Account ${msg.playerId} tried to configure bestia ${msg.playerBestiaId} it does not own" }
      return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED)
    }

    // Null also for a preset or place of another master, which an honest client cannot name.
    aiConfigs.apply(msg.playerBestiaId, orders, msg.presetId)
      ?: return deny(msg.playerId, AiConfigErrorSMSG.AiConfigErrorCode.INVALID_ORDERS)

    return true
  }

  private fun deny(playerId: Long, code: AiConfigErrorSMSG.AiConfigErrorCode): Boolean {
    outMessageProcessor.sendToPlayer(playerId, AiConfigErrorSMSG(code))
    return false
  }

  companion object {
    private val LOG = KotlinLogging.logger { }
  }
}
