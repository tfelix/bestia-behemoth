package net.bestia.zone.travel.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.travel.SavedRouteService
import org.springframework.stereotype.Component

@Component
class DeleteRouteHandler(
  private val routes: SavedRouteService,
  private val reply: SavedRoutesReply,
) : IoMessageHandler<DeleteRouteCMSG> {
  override val wire = decoder(MessageCase.DELETE_ROUTE) { accountId, envelope ->
    DeleteRouteCMSG(accountId, envelope.deleteRoute.routeId)
  }

  override fun handle(msg: DeleteRouteCMSG): Boolean {
    val masterId = reply.masterOf(msg.playerId) ?: return true
    if (routes.delete(masterId, msg.routeId)) reply.sendList(msg.playerId, masterId)
    return true
  }
}
