package net.bestia.zone.travel

import net.bestia.bus.contract.MapPoint
import net.bestia.zone.notebook.NotebookCollection
import net.bestia.zone.place.ecs.PlaceNameService
import net.bestia.zone.world.WorldService
import org.springframework.stereotype.Component
import kotlin.math.roundToLong

/**
 * The ways a master keeps, to send its entities along again by name and back the other way. Points are map
 * metres, as both apps' maps work in.
 */
@Component
class RoutesCollection(
  private val placeNames: PlaceNameService,
  private val worldService: WorldService,
  private val config: TravelConfig,
) : NotebookCollection<RoutesCollection.Route>(
  id = ID,
  type = Route::class.java,
  scope = Scope.MASTER,
  shape = Shape.KEYED,
  maxEntries = config.maxSavedRoutes,
  maxBodyBytes = MAX_BODY_BYTES,
  access = mapOf(Client.GAME to Access.WRITE, Client.COMPANION to Access.WRITE),
) {

  /** A null or blank [name] is made from where the route starts and ends whenever it is shown. */
  data class Route(val name: String? = null, val waypoints: List<MapPoint> = emptyList())

  /** [customName] is false while [name] is the made one, which follows the world. */
  data class Shown(val name: String, val customName: Boolean, val waypoints: List<MapPoint>)

  override fun validate(value: Route): String? {
    return when {
      value.waypoints.size < 2 -> "a route goes from somewhere to somewhere"
      value.waypoints.size > config.maxWaypoints -> "more than ${config.maxWaypoints} waypoints"
      value.waypoints.any { !it.east.isFinite() || !it.north.isFinite() } -> "a waypoint is not a place"
      (value.name?.length ?: 0) > MAX_NAME_LENGTH -> "a name longer than $MAX_NAME_LENGTH"
      else -> null
    }
  }

  override fun present(value: Route): Any {
    return shown(value)
  }

  fun shown(route: Route): Shown {
    val custom = route.name?.trim()?.takeIf { it.isNotEmpty() }
    return Shown(custom ?: nameFrom(route.waypoints), custom != null, route.waypoints)
  }

  /** "Ironhill → Ashford", from the places the route starts and ends in. */
  private fun nameFrom(waypoints: List<MapPoint>): String {
    val start = placeAt(waypoints.first())
    val end = placeAt(waypoints.last())
    return if (start == end) start else "$start → $end"
  }

  private fun placeAt(point: MapPoint): String {
    val voxelSize = worldService.config.voxelSize
    return placeNames.resolve((point.east / voxelSize).roundToLong(), (point.north / voxelSize).roundToLong()).name
  }

  companion object {
    const val ID = "routes"
    const val MAX_NAME_LENGTH = 40
    private const val MAX_BODY_BYTES = 4096
  }
}
