package net.bestia.zone.master.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.master.bestia.AiConfigErrorSMSG
import net.bestia.zone.master.bestia.BestiaAiConfigSMSG
import net.bestia.zone.master.bestia.BestiaAiConfigService
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.session.ConnectionInfoService
import org.springframework.stereotype.Component

/** Sends the orders of one of the player's bestias, which the orders window shows before anything changes. */
@Component
class GetBestiaOrdersHandler(
  private val connectionInfoService: ConnectionInfoService,
  private val aiConfigs: BestiaAiConfigService,
  private val outMessageProcessor: OutMessageProcessor,
) : IoMessageHandler<GetBestiaOrdersCMSG> {

  override val wire = decoder(MessageCase.GET_BESTIA_ORDERS) { accountId, envelope ->
    GetBestiaOrdersCMSG(accountId, envelope.getBestiaOrders.playerBestiaId)
  }

  override fun handle(msg: GetBestiaOrdersCMSG): Boolean {
    val stored = connectionInfoService.ownedBestiaOf(msg.playerId, msg.playerBestiaId)
      ?.let { aiConfigs.storedOf(msg.playerBestiaId) }
    if (stored == null) {
      outMessageProcessor.sendToPlayer(msg.playerId, AiConfigErrorSMSG(AiConfigErrorSMSG.AiConfigErrorCode.BESTIA_NOT_OWNED))
      return false
    }

    outMessageProcessor.sendToPlayer(msg.playerId, BestiaAiConfigSMSG(msg.playerBestiaId, stored))
    return true
  }
}
