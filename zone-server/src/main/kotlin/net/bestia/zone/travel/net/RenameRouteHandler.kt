package net.bestia.zone.travel.net

import net.bestia.bnet.proto.EnvelopeProto.Envelope.MessageCase
import net.bestia.zone.message.IoMessageHandler
import net.bestia.zone.message.decoder
import net.bestia.zone.travel.SavedRouteService
import org.springframework.stereotype.Component

@Component
class RenameRouteHandler(
  private val routes: SavedRouteService,
  private val reply: SavedRoutesReply,
) : IoMessageHandler<RenameRouteCMSG> {
  override val wire = decoder(MessageCase.RENAME_ROUTE) { accountId, envelope ->
    RenameRouteCMSG(accountId, envelope.renameRoute.routeId, envelope.renameRoute.name)
  }

  override fun handle(msg: RenameRouteCMSG): Boolean {
    val masterId = reply.masterOf(msg.playerId) ?: return true
    if (routes.rename(masterId, msg.routeId, msg.name)) reply.sendList(msg.playerId, masterId)
    return true
  }
}
