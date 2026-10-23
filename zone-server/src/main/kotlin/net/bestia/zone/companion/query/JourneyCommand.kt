package net.bestia.zone.companion.query

import io.github.oshai.kotlinlogging.KotlinLogging
import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.account.persistence.PlayerBestiaRepository
import net.bestia.zone.ecs.core.WorldView
import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.message.AccountTaskExecutor
import net.bestia.zone.travel.JourneyOrder
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.travel.SavedRouteService
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service

/**
 * Sends an owned bestia off, or stops it, from the companion app - the same journey a map click in the game
 * starts. Run in the account's inbox, so it is ordered with what the game client sends at the same moment.
 */
@Service
class JourneyCommand(
  private val owned: OwnedMasters,
  private val playerBestias: PlayerBestiaRepository,
  private val routes: SavedRouteService,
  private val journeys: JourneyService,
  private val world: WorldView,
  private val plane: MapPlane,
  private val inbox: AccountTaskExecutor,
) {

  fun send(correlationId: String, accountId: Long, query: ZoneQuery.SendOnJourney): BusReply {
    return inInbox(correlationId, accountId) { sendOwned(correlationId, accountId, query) }
  }

  fun stop(correlationId: String, accountId: Long, query: ZoneQuery.StopJourney): BusReply {
    return inInbox(correlationId, accountId) {
      val entityId = bestiaEntity(accountId, query.masterId, query.playerBestiaId)
      val stopped = entityId?.let { world.modify(it) { id -> journeys.cancel(this, id) } }
      if (stopped == null) resting(correlationId) else BusReply.ok(correlationId, ZoneAnswer.Done)
    }
  }

  private fun sendOwned(correlationId: String, accountId: Long, query: ZoneQuery.SendOnJourney): BusReply {
    val entityId = bestiaEntity(accountId, query.masterId, query.playerBestiaId) ?: return resting(correlationId)
    val (waypoints, name) = waypointsOf(query) ?: return BusReply.of(correlationId, Status.NOT_FOUND, "no such route")
    if (waypoints.isEmpty()) return BusReply.of(correlationId, Status.INVALID, "a journey needs a destination")

    val refusal = world.modify(entityId) { id ->
      journeys.start(this, id, waypoints, JourneyOrder.PLAYER, name, query.savedRouteId)
    } ?: return resting(correlationId)

    if (refusal != null) return BusReply.of(correlationId, Status.INVALID, refusal.name)
    return BusReply.ok(correlationId, ZoneAnswer.Done)
  }

  /** The route's points in the order asked for, with its name; null for a route that is not the master's. */
  private fun waypointsOf(query: ZoneQuery.SendOnJourney): Pair<List<Vec3L>, String?>? {
    val routeId = query.savedRouteId ?: return query.waypoints.map { plane.positionAt(it) } to null

    val route = routes.find(query.masterId, routeId) ?: return null
    val points = if (query.reverse) route.waypoints.reversed() else route.waypoints
    return points to route.name
  }

  /** The bestia's entity while it is in the world, null when it is not the master's or rests until it returns. */
  private fun bestiaEntity(accountId: Long, masterId: Long, playerBestiaId: Long): Long? {
    owned.find(accountId, masterId) ?: return null
    val row = playerBestias.findByIdOrNull(playerBestiaId)?.takeIf { it.master.id == masterId } ?: return null
    return row.entityId
  }

  private fun resting(correlationId: String): BusReply {
    return BusReply.of(correlationId, Status.NOT_FOUND, "not in the world until its master returns")
  }

  /** Never lets [work] throw: a failure in an account's inbox closes that account's game connection. */
  private fun inInbox(correlationId: String, accountId: Long, work: () -> BusReply): BusReply {
    var reply = BusReply.of(correlationId, Status.NOT_FOUND)
    inbox.onIo(accountId) {
      reply = try {
        work()
      } catch (e: Exception) {
        LOG.error(e) { "Journey order of account $accountId failed" }
        BusReply.of(correlationId, Status.BUSY, "internal error")
      }
    }.join()

    return reply
  }

  private companion object {
    val LOG = KotlinLogging.logger { }
  }
}
