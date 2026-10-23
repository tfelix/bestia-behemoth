package net.bestia.api.rest

import net.bestia.api.auth.CompanionAuthFilter.Companion.ACCOUNT_ID
import net.bestia.api.bus.ZoneClient
import net.bestia.bus.contract.MapPoint
import net.bestia.bus.contract.ZoneAnswer
import net.bestia.bus.contract.ZoneQuery
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * The ways a master keeps to send its bestias along again, the same list the game shows. Every write answers with
 * the whole list as it is now. A 400 with ROUTE_LIMIT means the master keeps as many as it may.
 */
@RestController
@RequestMapping("/v1/companion/masters/{masterId}/routes")
class RoutesController(
  private val zone: ZoneClient,
) {

  /** [customName] is false while [name] is the one made from where the route starts and ends. */
  data class Route(
    val id: Long,
    val name: String,
    val customName: Boolean,
    val waypoints: List<MastersController.Position>,
  )

  /** A blank [name] lets the zone name it from where it starts and ends. */
  data class NewRoute(val waypoints: List<MastersController.Position>, val name: String = "")

  /** A blank [name] gives the route its made name back. */
  data class Rename(val name: String)

  @GetMapping
  fun list(@RequestAttribute(ACCOUNT_ID) accountId: Long, @PathVariable masterId: Long): List<Route> {
    return routesOf(zone.ask(accountId, ZoneQuery.SavedRoutes(masterId)).answerOrThrow())
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  fun save(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody route: NewRoute,
  ): List<Route> {
    val waypoints = route.waypoints.map { MapPoint(it.eastMetres, it.northMetres) }
    val query = ZoneQuery.SaveRoute(masterId, waypoints, route.name)
    return routesOf(zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow())
  }

  @PatchMapping("/{routeId}")
  fun rename(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable routeId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
    @RequestBody rename: Rename,
  ): List<Route> {
    val query = ZoneQuery.RenameRoute(masterId, routeId, rename.name)
    return routesOf(zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow())
  }

  @DeleteMapping("/{routeId}")
  fun delete(
    @RequestAttribute(ACCOUNT_ID) accountId: Long,
    @PathVariable masterId: Long,
    @PathVariable routeId: Long,
    @RequestHeader("Idempotency-Key") idempotencyKey: String,
  ): List<Route> {
    val query = ZoneQuery.DeleteRoute(masterId, routeId)
    return routesOf(zone.ask(accountId, query, idempotencyKey.take(MAX_KEY_LENGTH)).answerOrThrow())
  }

  private fun routesOf(list: ZoneAnswer.RouteList): List<Route> {
    return list.routes.map { route ->
      Route(
        id = route.id,
        name = route.name,
        customName = route.customName,
        waypoints = route.waypoints.map { MastersController.Position(it.east, it.north) },
      )
    }
  }

  private companion object {
    const val MAX_KEY_LENGTH = 64
  }
}
