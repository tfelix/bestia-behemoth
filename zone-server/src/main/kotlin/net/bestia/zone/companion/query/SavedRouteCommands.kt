package net.bestia.zone.companion.query

import net.bestia.bus.contract.BusReply
import net.bestia.bus.contract.BusReply.Status
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import net.bestia.zone.travel.SavedRouteService
import org.springframework.stereotype.Service

/** A master's saved routes, kept and changed from the companion app the same way the game does it. */
@Service
class SavedRouteCommands(
  private val owned: OwnedMasters,
  private val routes: SavedRouteService,
  private val plane: MapPlane,
) {

  fun list(correlationId: String, accountId: Long, query: ZoneQuery.SavedRoutes): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    return BusReply.ok(correlationId, routeList(query.masterId))
  }

  fun save(correlationId: String, accountId: Long, query: ZoneQuery.SaveRoute): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    if (query.waypoints.size < 2) return BusReply.of(correlationId, Status.INVALID, "a route needs two points")

    routes.save(query.masterId, query.waypoints.map { plane.positionAt(it) }, query.name)
      ?: return BusReply.of(correlationId, Status.INVALID, "ROUTE_LIMIT")
    return BusReply.ok(correlationId, routeList(query.masterId))
  }

  fun rename(correlationId: String, accountId: Long, query: ZoneQuery.RenameRoute): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    if (!routes.rename(query.masterId, query.routeId, query.name)) return BusReply.of(correlationId, Status.NOT_FOUND)
    return BusReply.ok(correlationId, routeList(query.masterId))
  }

  fun delete(correlationId: String, accountId: Long, query: ZoneQuery.DeleteRoute): BusReply {
    owned.find(accountId, query.masterId) ?: return BusReply.of(correlationId, Status.NOT_FOUND)
    if (!routes.delete(query.masterId, query.routeId)) return BusReply.of(correlationId, Status.NOT_FOUND)
    return BusReply.ok(correlationId, routeList(query.masterId))
  }

  private fun routeList(masterId: Long): ZoneAnswer.RouteList {
    return ZoneAnswer.RouteList(
      routes.list(masterId).map { route ->
        ZoneAnswer.RouteList.Route(route.id, route.name, route.customName, route.waypoints.map { plane.pointOf(it) })
      }
    )
  }
}
