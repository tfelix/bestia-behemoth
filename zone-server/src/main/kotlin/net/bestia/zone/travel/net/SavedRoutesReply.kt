package net.bestia.zone.travel.net

import net.bestia.zone.message.OutMessageProcessor
import net.bestia.zone.session.ConnectionInfoService
import net.bestia.zone.travel.SavedRouteService
import net.bestia.zone.travel.SavedRoutesSMSG
import org.springframework.stereotype.Component

/** Answers every saved-route message the same way: with the master's whole list as it is now. */
@Component
class SavedRoutesReply(
  private val connections: ConnectionInfoService,
  private val routes: SavedRouteService,
  private val out: OutMessageProcessor,
) {

  fun masterOf(accountId: Long): Long? {
    return connections.activeMasterIdOf(accountId)
  }

  fun sendList(accountId: Long, masterId: Long) {
    out.sendToPlayer(accountId, SavedRoutesSMSG(routes.list(masterId)))
  }
}
