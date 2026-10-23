package net.bestia.zone.travel

import net.bestia.zone.geometry.Vec3L
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.travel.persistence.SavedRoute
import net.bestia.zone.travel.persistence.SavedRouteRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * The routes a master keeps, for the game client and the companion app alike. Off the tick: every call reads
 * or writes the database.
 */
@Service
class SavedRouteService(
  private val repository: SavedRouteRepository,
  private val placeNames: PlaceNameService,
  private val config: TravelConfig,
) {

  data class View(val id: Long, val name: String, val customName: Boolean, val waypoints: List<Vec3L>)

  @Transactional(readOnly = true)
  fun list(masterId: Long): List<View> {
    return repository.findAllByMasterIdOrderById(masterId).map { viewOf(it) }
  }

  @Transactional(readOnly = true)
  fun find(masterId: Long, routeId: Long): View? {
    return repository.findById(routeId).orElse(null)?.takeIf { it.masterId == masterId }?.let { viewOf(it) }
  }

  /**
   * Keeps [waypoints] under [name], or under one made from where they start and end when it is blank.
   *
   * @return null when the master keeps as many routes as it may
   */
  @Transactional
  fun save(masterId: Long, waypoints: List<Vec3L>, name: String): View? {
    require(waypoints.size >= 2) { "A route goes from somewhere to somewhere" }
    if (repository.countByMasterId(masterId) >= config.maxSavedRoutes) return null

    val kept = waypoints.take(config.maxWaypoints)
    val custom = cleaned(name)
    val route = SavedRoute(
      masterId = masterId,
      name = custom ?: nameFrom(kept),
      customName = custom != null,
      waypoints = WaypointText.encode(kept),
    )
    return viewOf(repository.save(route))
  }

  /** A blank name gives the route its made name back. */
  @Transactional
  fun rename(masterId: Long, routeId: Long, name: String): Boolean {
    val route = repository.findById(routeId).orElse(null)?.takeIf { it.masterId == masterId } ?: return false
    val custom = cleaned(name)

    route.name = custom ?: nameFrom(WaypointText.decode(route.waypoints))
    route.customName = custom != null
    repository.save(route)
    return true
  }

  @Transactional
  fun delete(masterId: Long, routeId: Long): Boolean {
    val route = repository.findById(routeId).orElse(null)?.takeIf { it.masterId == masterId } ?: return false
    repository.delete(route)
    return true
  }

  /** "Ironhill → Ashford", from the places the route starts and ends in. */
  private fun nameFrom(waypoints: List<Vec3L>): String {
    val start = placeNames.resolve(waypoints.first().x, waypoints.first().y).name
    val end = placeNames.resolve(waypoints.last().x, waypoints.last().y).name
    return if (start == end) start else "$start → $end"
  }

  private fun cleaned(name: String): String? {
    return name.trim().take(MAX_NAME_LENGTH).takeIf { it.isNotEmpty() }
  }

  private fun viewOf(route: SavedRoute): View {
    return View(route.id, route.name, route.customName, WaypointText.decode(route.waypoints))
  }

  private companion object {
    const val MAX_NAME_LENGTH = 40
  }
}
