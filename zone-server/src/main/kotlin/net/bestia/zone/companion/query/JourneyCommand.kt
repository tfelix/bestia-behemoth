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
import net.bestia.zone.travel.JourneyRefusal
import net.bestia.zone.travel.JourneyService
import net.bestia.zone.notebook.NotebookService
import net.bestia.zone.travel.RoutesCollection
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
  private val notebook: NotebookService,
  private val routes: RoutesCollection,
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
    val (waypoints, name) = waypointsOf(accountId, query) ?: return BusReply.of(correlationId, Status.NOT_FOUND, "no such route")
    if (waypoints.isEmpty()) return BusReply.of(correlationId, Status.INVALID, "a journey needs a destination")

    // Wrapped, because modify's null means the entity is gone, and start's null means it set off.
    val attempt = world.modify(entityId) { id ->
      Attempt(journeys.start(this, id, waypoints, JourneyOrder.PLAYER, name, query.routeKey))
    } ?: return resting(correlationId)

    val refusal = attempt.refusal ?: return BusReply.ok(correlationId, ZoneAnswer.Done)
    return BusReply.of(correlationId, Status.INVALID, refusal.name)
  }

  private class Attempt(val refusal: JourneyRefusal?)

  /** The route's points in the order asked for, with its name; null for a route that is not the master's. */
  private fun waypointsOf(accountId: Long, query: ZoneQuery.SendOnJourney): Pair<List<Vec3L>, String?>? {
    val routeKey = query.routeKey ?: return query.waypoints.map { plane.positionAt(it) } to null

    val route = notebook.read(NotebookService.Owner(accountId, query.masterId), routes, routeKey) ?: return null
    val shown = routes.shown(route)
    val points = if (query.reverse) shown.waypoints.reversed() else shown.waypoints
    return points.map { plane.positionAt(it) } to shown.name
  }

  /** The bestia's entity while it is in the world, null when it is not the master's. */
  private fun bestiaEntity(accountId: Long, masterId: Long, playerBestiaId: Long): Long? {
    owned.find(accountId, masterId) ?: return null
    val row = playerBestias.findByIdOrNull(playerBestiaId)?.takeIf { it.master.id == masterId } ?: return null
    return row.entityId
  }

  private fun resting(correlationId: String): BusReply {
    return BusReply.of(correlationId, Status.NOT_FOUND, "not in the world")
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
