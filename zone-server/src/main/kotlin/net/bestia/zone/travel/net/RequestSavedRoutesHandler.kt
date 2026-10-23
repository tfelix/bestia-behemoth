package net.bestia.zone.travel.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import org.springframework.stereotype.Component

@Component
class RequestSavedRoutesHandler(
  private val reply: SavedRoutesReply,
) : IoMessageHandler<RequestSavedRoutesCMSG> {
  override val wire = decoder(MessageCase.REQUEST_SAVED_ROUTES) { accountId, _ -> RequestSavedRoutesCMSG(accountId) }

  override fun handle(msg: RequestSavedRoutesCMSG): Boolean {
    val masterId = reply.masterOf(msg.playerId) ?: return true
    reply.sendList(msg.playerId, masterId)
    return true
  }
}
