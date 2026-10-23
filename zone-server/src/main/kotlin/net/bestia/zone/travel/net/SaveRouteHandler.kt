package net.bestia.zone.travel.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.bnet.proto.OperationErrorProto
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.OperationErrorSMSG
import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.message.decoder
import net.bestia.zone.travel.SavedRouteService
import org.springframework.stereotype.Component

@Component
class SaveRouteHandler(
  private val routes: SavedRouteService,
  private val reply: SavedRoutesReply,
  private val out: OutMessageProcessor,
) : IoMessageHandler<SaveRouteCMSG> {
  override val wire = decoder(MessageCase.SAVE_ROUTE) { accountId, envelope ->
    val save = envelope.saveRoute
    SaveRouteCMSG(accountId, save.waypointsList.map { Vec3L(it.x, it.y, it.z) }, save.name)
  }

  override fun handle(msg: SaveRouteCMSG): Boolean {
    val masterId = reply.masterOf(msg.playerId) ?: return true
    // An honest client only offers to save a route that goes somewhere.
    if (msg.waypoints.size < 2) return true

    if (routes.save(masterId, msg.waypoints, msg.name) == null) {
      out.sendToPlayer(msg.playerId, OperationErrorSMSG(OperationErrorProto.OpError.ROUTE_LIMIT))
      return true
    }

    reply.sendList(msg.playerId, masterId)
    return true
  }
}
